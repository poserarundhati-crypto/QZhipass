package org.microsoft.qintelipass.services.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.models.Models;
import org.microsoft.qintelipass.repository.ModelsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class OpenAiCompatibleChatModelProvider implements ChatModelProvider {
    private static final Set<String> SUPPORTED_ROLES = Set.of("system", "user", "assistant");

    private final ModelsRepository modelsRepository;
    private final RestClient restClient;

    public OpenAiCompatibleChatModelProvider(
            ModelsRepository modelsRepository,
            @Value("${app.chat.provider.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.chat.provider.read-timeout:120s}") Duration readTimeout) {
        this.modelsRepository = modelsRepository;
        this.restClient = createRestClient(connectTimeout, readTimeout);
    }

    @Override
    public ChatCompletionResult complete(
            String modelName,
            List<ModelChatMessage> messages,
            ChatOptions options) {
        try {
            String normalizedModelName = requireModelName(modelName);
            ChatOptions validatedOptions = requireOptions(options);
            List<Map<String, String>> requestMessages = requireMessages(messages);

            Models model = modelsRepository.findByModelNameIgnoreCase(normalizedModelName)
                    .orElseThrow(ProviderCallException::new);
            validateModelConfig(model);

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model.getModelName());
            requestBody.put("messages", requestMessages);
            requestBody.put("temperature", validatedOptions.temperature());
            requestBody.put("max_tokens", validatedOptions.numPredict());
            requestBody.put("stream", false);

            ChatCompletionResponse response = restClient.post()
                    .uri(resolveChatCompletionsUri(model.getApiBase()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + model.getApiKey().trim())
                    .body(requestBody)
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            return toResult(model.getId(), messages, response);
        } catch (ProviderCallException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Chat model provider call failed: exception={}", exception.getClass().getSimpleName());
            throw new ProviderCallException();
        }
    }

    private static RestClient createRestClient(Duration connectTimeout, Duration readTimeout) {
        requirePositiveTimeout(connectTimeout);
        requirePositiveTimeout(readTimeout);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    private static void requirePositiveTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Chat provider timeout must be positive");
        }
    }

    private String requireModelName(String modelName) {
        if (!StringUtils.hasText(modelName)) {
            throw new ProviderCallException();
        }
        return modelName.trim();
    }

    private ChatOptions requireOptions(ChatOptions options) {
        if (options == null
                || !Double.isFinite(options.temperature())
                || options.temperature() < 0D
                || options.temperature() > 2D
                || options.numPredict() <= 0) {
            throw new ProviderCallException();
        }
        return options;
    }

    private List<Map<String, String>> requireMessages(List<ModelChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new ProviderCallException();
        }

        return messages.stream()
                .map(this::toRequestMessage)
                .toList();
    }

    private Map<String, String> toRequestMessage(ModelChatMessage message) {
        if (message == null || !StringUtils.hasText(message.role()) || !StringUtils.hasText(message.content())) {
            throw new ProviderCallException();
        }

        String role = message.role().trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_ROLES.contains(role)) {
            throw new ProviderCallException();
        }
        return Map.of("role", role, "content", message.content());
    }

    private void validateModelConfig(Models model) {
        if (model.getId() == null
                || !StringUtils.hasText(model.getModelName())
                || !StringUtils.hasText(model.getApiBase())
                || !StringUtils.hasText(model.getApiKey())) {
            throw new ProviderCallException();
        }
    }

    private URI resolveChatCompletionsUri(String apiBase) {
        URI baseUri = URI.create(apiBase.trim());
        String scheme = baseUri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || !StringUtils.hasText(baseUri.getRawAuthority())
                || baseUri.getRawQuery() != null
                || baseUri.getRawFragment() != null) {
            throw new ProviderCallException();
        }

        String normalizedBase = baseUri.toString().replaceFirst("/+$", "");
        String endpoint = normalizedBase.endsWith("/chat/completions")
                ? normalizedBase
                : normalizedBase + "/chat/completions";
        return URI.create(endpoint);
    }

    private ChatCompletionResult toResult(
            Long modelId,
            List<ModelChatMessage> messages,
            ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new ProviderCallException();
        }

        Choice firstChoice = response.choices().get(0);
        String content = firstChoice == null || firstChoice.message() == null
                ? null
                : firstChoice.message().content();
        if (!StringUtils.hasText(content)) {
            throw new ProviderCallException();
        }

        int estimatedPromptTokens = estimatePromptTokens(messages);
        int estimatedCompletionTokens = estimateTextTokens(content);
        Usage usage = response.usage();
        int promptTokens = positiveOrEstimate(usage == null ? null : usage.promptTokens(), estimatedPromptTokens);
        int completionTokens = positiveOrEstimate(
                usage == null ? null : usage.completionTokens(), estimatedCompletionTokens);
        int calculatedTotal = safeTokenSum(promptTokens, completionTokens);
        int totalTokens = positiveOrEstimate(usage == null ? null : usage.totalTokens(), calculatedTotal);
        totalTokens = Math.max(totalTokens, calculatedTotal);

        return new ChatCompletionResult(
                modelId,
                content,
                promptTokens,
                completionTokens,
                totalTokens);
    }

    private int estimatePromptTokens(List<ModelChatMessage> messages) {
        long estimated = 2L;
        for (ModelChatMessage message : messages) {
            estimated += 4L;
            estimated += estimateTextTokens(message.role());
            estimated += estimateTextTokens(message.content());
            if (estimated >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) estimated;
    }

    private int estimateTextTokens(String text) {
        int utf8Bytes = text.getBytes(StandardCharsets.UTF_8).length;
        // Without a provider tokenizer, one token per UTF-8 byte is a conservative upper bound.
        return Math.max(1, utf8Bytes);
    }

    private int positiveOrEstimate(Integer reported, int estimate) {
        return reported != null && reported > 0 ? reported : estimate;
    }

    private int safeTokenSum(int left, int right) {
        long total = (long) left + right;
        return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatCompletionResponse(List<Choice> choices, Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(ChatMessage message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatMessage(String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens) {
    }
}
