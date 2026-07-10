package org.microsoft.qintelipass.response;

import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;
import org.microsoft.qintelipass.entity.Conversation;

import java.time.LocalDateTime;

public record ConversationResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long conversationId,
        String title,
        String modelKey,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastMessageAt
) {
    public static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getId(),
                conversation.getTitle(),
                conversation.getModelKey(),
                conversation.getStatus(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                conversation.getLastMessageAt()
        );
    }
}
