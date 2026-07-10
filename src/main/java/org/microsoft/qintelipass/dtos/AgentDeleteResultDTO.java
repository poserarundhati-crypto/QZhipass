package org.microsoft.qintelipass.dtos;

public record AgentDeleteResultDTO(
        String agentId,
        String agentName,
        boolean deleted,
        boolean alreadyDeleted) {
}
