package org.microsoft.qintelipass.services.chat;

public record ChatCompletionResult(
        Long modelId,
        String content,
        int promptTokens,
        int completionTokens,
        int totalTokens) {
}
