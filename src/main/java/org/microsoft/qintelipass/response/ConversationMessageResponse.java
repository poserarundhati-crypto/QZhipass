package org.microsoft.qintelipass.response;

import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;
import org.microsoft.qintelipass.entity.ConversationMessage;

import java.time.LocalDateTime;

public record ConversationMessageResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long conversationId,
        String role,
        String content,
        String modelKey,
        LocalDateTime createdAt
) {
    public static ConversationMessageResponse from(ConversationMessage message) {
        return new ConversationMessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getRole().name(),
                message.getContent(),
                message.getModelKey(),
                message.getCreatedAt()
        );
    }
}
