package org.microsoft.qintelipass.dtos;

public record AgentDeleteConfirmationDTO(
        String agentId,
        String agentName,
        boolean deletable,
        String confirmationMessage) {
}
