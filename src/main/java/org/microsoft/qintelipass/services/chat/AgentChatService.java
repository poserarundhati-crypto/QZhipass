package org.microsoft.qintelipass.services.chat;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.dtos.AgentInvocationConfig;
import org.microsoft.qintelipass.entity.ConversationMessageRole;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.request.ChatMessageRequest;
import org.microsoft.qintelipass.request.ChatOptionsRequest;
import org.microsoft.qintelipass.request.KnowledgeChatRequest;
import org.microsoft.qintelipass.response.ConversationDetailResponse;
import org.microsoft.qintelipass.response.chat.AgentStatusEvent;
import org.microsoft.qintelipass.response.chat.ChatDoneEvent;
import org.microsoft.qintelipass.response.chat.ChatErrorEvent;
import org.microsoft.qintelipass.response.chat.MessageDeltaEvent;
import org.microsoft.qintelipass.services.AgentService;
import org.microsoft.qintelipass.services.CensorService;
import org.microsoft.qintelipass.services.ConversationService;
import org.microsoft.qintelipass.services.TokenUsageService;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class AgentChatService {
    public static final String AGENT_INVOKE_FAILED = "AGENT_INVOKE_FAILED";
    public static final String AGENT_FAILURE_MESSAGE = "Agent调用失败，请稍后重试。";
    public static final String CHAT_INVOKE_FAILED = "CHAT_INVOKE_FAILED";
    public static final String CHAT_FAILURE_MESSAGE = "对话调用失败，请稍后重试。";

    private static final int MAX_MESSAGE_LENGTH = 20_000;
    private static final int MAX_NUM_PREDICT = 8192;

    private final AgentService agentService;
    private final ConversationService conversationService;
    private final PromptComposer promptComposer;
    private final AgentResponseFormatValidator formatValidator;
    private final ChatModelProvider chatModelProvider;
    private final TokenUsageService tokenUsageService;
    private final CensorService censorService;
    private final UserService userService;

    public AgentChatService(
            AgentService agentService,
            ConversationService conversationService,
            PromptComposer promptComposer,
            AgentResponseFormatValidator formatValidator,
            ChatModelProvider chatModelProvider,
            TokenUsageService tokenUsageService,
            CensorService censorService,
            UserService userService
    ) {
        this.agentService = agentService;
        this.conversationService = conversationService;
        this.promptComposer = promptComposer;
        this.formatValidator = formatValidator;
        this.chatModelProvider = chatModelProvider;
        this.tokenUsageService = tokenUsageService;
        this.censorService = censorService;
        this.userService = userService;
    }

    public void streamChat(
            Long currentUserId,
            KnowledgeChatRequest request,
            String requestId,
            ChatEventSink eventSink
    ) {
        boolean agentRequested = request != null && request.agentId() != null;
        try {
            executeChat(currentUserId, request, eventSink);
        } catch (Exception exception) {
            log.warn(
                    "Chat invocation failed: requestId={}, userId={}, agentId={}, sessionId={}, exceptionType={}",
                    requestId,
                    currentUserId,
                    request == null ? null : request.agentId(),
                    request == null ? null : request.sessionId(),
                    exception.getClass().getSimpleName());
            sendFailure(eventSink, agentRequested, requestId);
        }
    }

    private void executeChat(
            Long currentUserId,
            KnowledgeChatRequest request,
            ChatEventSink eventSink
    ) throws Exception {
        requireRequest(currentUserId, request);
        String currentQuestion = extractCurrentQuestion(request.messages());
        ChatOptions options = validateOptions(request.options());

        ConversationDetailResponse conversation = conversationService.getConversation(
                currentUserId, request.sessionId());
        AgentInvocationConfig agent = request.agentId() == null
                ? null
                : agentService.requireCallableAgent(currentUserId, request.agentId());

        if (agent != null) {
            eventSink.send("agent-status", new AgentStatusEvent(
                    "STARTED",
                    agent.agentId().toString(),
                    agent.agentName(),
                    "正在调用" + agent.agentName() + "来构思回答..."));
        }

        String modelName = resolveModelName(request.modelName(), conversation, agent);
        boolean numberedListRequired = agent != null
                && formatValidator.requiresNumberedList(agent.prompt());
        List<ModelChatMessage> modelMessages = agent == null
                ? promptComposer.composeNormalMessages(conversation.messages(), currentQuestion)
                : promptComposer.composeAgentMessages(
                        agent.prompt(), conversation.messages(), currentQuestion, numberedListRequired);

        ChatCompletionResult completion = chatModelProvider.complete(modelName, modelMessages, options);
        int totalTokens = requireTokenCount(completion);
        if (!formatValidator.isValid(completion.content(), numberedListRequired)) {
            if (!numberedListRequired) {
                throw new IllegalStateException("Provider returned an empty response.");
            }
            ChatCompletionResult retryCompletion = chatModelProvider.complete(
                    modelName,
                    promptComposer.withNumberedListRetryInstruction(modelMessages),
                    options);
            totalTokens = addTokenCounts(totalTokens, requireTokenCount(retryCompletion));
            completion = retryCompletion;
            if (!formatValidator.isValid(completion.content(), true)) {
                throw new IllegalStateException("Provider response did not satisfy the Agent format.");
            }
        }

        boolean quotaRecorded = agent == null
                ? tokenUsageService.tryRecordTokenUsageWithinLimit(
                        currentUserId, completion.modelId(), totalTokens)
                : agentService.tryRecordActiveAgentCall(
                        currentUserId, agent.agentId(), completion.modelId(), totalTokens);
        if (!quotaRecorded) {
            throw new IllegalStateException("Token quota is insufficient.");
        }

        User user = userService.getUserById(currentUserId);
        if (user == null) {
            throw new SecurityException("Authenticated user no longer exists.");
        }
        censorService.checkAndRecord(
                currentUserId,
                user.getName(),
                user.getPhone(),
                StringUtils.hasText(user.getDepartment()) ? user.getDepartment() : "未分配",
                modelName,
                currentQuestion,
                completion.content());

        Long agentId = agent == null ? null : agent.agentId();
        conversationService.saveGeneratedExchange(
                currentUserId,
                request.sessionId(),
                currentQuestion,
                completion.content(),
                conversation.conversation().modelKey(),
                agentId);

        eventSink.send("message-delta", new MessageDeltaEvent(completion.content()));
        eventSink.send("done", new ChatDoneEvent(
                "COMPLETED",
                agentId == null ? null : agentId.toString()));
    }

    private void requireRequest(Long currentUserId, KnowledgeChatRequest request) {
        if (currentUserId == null) {
            throw new SecurityException("Missing authenticated user.");
        }
        if (request == null || request.sessionId() == null || request.sessionId() <= 0) {
            throw new IllegalArgumentException("sessionId is required.");
        }
        if (request.agentId() != null && request.agentId() <= 0) {
            throw new IllegalArgumentException("agentId is invalid.");
        }
    }

    private String extractCurrentQuestion(List<ChatMessageRequest> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages must contain a user question.");
        }
        for (ChatMessageRequest message : messages) {
            if (message == null || !StringUtils.hasText(message.role())) {
                throw new IllegalArgumentException("Every message must have a role.");
            }
            String role = message.role().trim().toLowerCase(Locale.ROOT);
            if (!"user".equals(role) && !"assistant".equals(role)) {
                throw new IllegalArgumentException("Client system messages are not allowed.");
            }
        }
        ChatMessageRequest lastMessage = messages.get(messages.size() - 1);
        if (!"user".equals(lastMessage.role().trim().toLowerCase(Locale.ROOT))
                || !StringUtils.hasText(lastMessage.content())) {
            throw new IllegalArgumentException("The final message must be a user question.");
        }
        String question = lastMessage.content().trim();
        if (question.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("The user question is too long.");
        }
        return question;
    }

    private ChatOptions validateOptions(ChatOptionsRequest requestOptions) {
        ChatOptionsRequest effective = requestOptions == null
                ? new ChatOptionsRequest(null, null)
                : requestOptions;
        double temperature = effective.effectiveTemperature();
        int numPredict = effective.effectiveNumPredict();
        if (!Double.isFinite(temperature) || temperature < 0.0d || temperature > 2.0d) {
            throw new IllegalArgumentException("temperature must be between 0 and 2.");
        }
        if (numPredict < 1 || numPredict > MAX_NUM_PREDICT) {
            throw new IllegalArgumentException("numPredict must be between 1 and 8192.");
        }
        return new ChatOptions(temperature, numPredict);
    }

    private String resolveModelName(
            String requestedModel,
            ConversationDetailResponse conversation,
            AgentInvocationConfig agent
    ) {
        if (StringUtils.hasText(requestedModel)) {
            return requestedModel.trim();
        }
        if (agent != null && StringUtils.hasText(agent.baseModel())) {
            return agent.baseModel().trim();
        }
        String conversationModel = conversation.conversation().modelKey();
        if (!StringUtils.hasText(conversationModel)) {
            throw new IllegalArgumentException("modelName is required.");
        }
        return conversationModel.trim();
    }

    private int requireTokenCount(ChatCompletionResult completion) {
        if (completion == null
                || completion.modelId() == null
                || completion.modelId() <= 0
                || !StringUtils.hasText(completion.content())
                || completion.totalTokens() <= 0) {
            throw new IllegalStateException("Provider returned an invalid completion.");
        }
        return completion.totalTokens();
    }

    private int addTokenCounts(int left, int right) {
        long result = (long) left + right;
        if (result > Integer.MAX_VALUE) {
            throw new IllegalStateException("Provider token usage is invalid.");
        }
        return (int) result;
    }

    private void sendFailure(ChatEventSink eventSink, boolean agentRequested, String requestId) {
        try {
            eventSink.send("error", new ChatErrorEvent(
                    agentRequested ? AGENT_INVOKE_FAILED : CHAT_INVOKE_FAILED,
                    agentRequested ? AGENT_FAILURE_MESSAGE : CHAT_FAILURE_MESSAGE,
                    requestId));
        } catch (Exception sendException) {
            log.warn(
                    "Could not send chat failure event: requestId={}, exceptionType={}",
                    requestId,
                    sendException.getClass().getSimpleName());
        }
    }
}
