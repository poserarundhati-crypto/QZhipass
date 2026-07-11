package org.microsoft.qintelipass.request;

import java.util.List;

public record KnowledgeChatRequest(
        Long sessionId,
        Long agentId,
        String modelName,
        List<ChatMessageRequest> messages,
        ChatOptionsRequest options
) {
}
