package org.microsoft.qintelipass.dtos;

public record AgentInvocationConfig(
        Long agentId,
        String agentName,
        String prompt,
        String baseModel
) {
}
