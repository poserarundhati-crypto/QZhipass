package org.microsoft.qintelipass.response.chat;

public record AgentStatusEvent(
        String phase,
        String agentId,
        String agentName,
        String message
) {
}
