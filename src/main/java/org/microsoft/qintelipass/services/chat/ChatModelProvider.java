package org.microsoft.qintelipass.services.chat;

import java.util.List;

public interface ChatModelProvider {
    ChatCompletionResult complete(
            String modelName,
            List<ModelChatMessage> messages,
            ChatOptions options);
}
