package org.microsoft.qintelipass.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.microsoft.qintelipass.request.KnowledgeChatRequest;
import org.microsoft.qintelipass.services.CurrentUserService;
import org.microsoft.qintelipass.services.chat.AgentChatService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeChatController {
    private static final long SSE_TIMEOUT_MILLIS = 120_000L;

    private final AgentChatService agentChatService;
    private final CurrentUserService currentUserService;
    private final TaskExecutor taskExecutor;

    public KnowledgeChatController(
            AgentChatService agentChatService,
            CurrentUserService currentUserService,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.agentChatService = agentChatService;
        this.currentUserService = currentUserService;
        this.taskExecutor = taskExecutor;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @RequestBody KnowledgeChatRequest request,
            HttpServletRequest httpRequest
    ) {
        Long currentUserId = currentUserService.requireUserId(httpRequest);
        String requestId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        emitter.onTimeout(emitter::complete);

        taskExecutor.execute(() -> {
            try {
                agentChatService.streamChat(
                        currentUserId,
                        request,
                        requestId,
                        (eventName, data) -> emitter.send(SseEmitter.event()
                                .name(eventName)
                                .data(data, MediaType.APPLICATION_JSON)));
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }
}
