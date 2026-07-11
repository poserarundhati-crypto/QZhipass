package org.microsoft.qintelipass.dtos;

public record CallableAgentDTO(
        String agentId,
        String agentName,
        boolean available,
        String baseModel,
        String createdBy,
        boolean allowed
) {
}
