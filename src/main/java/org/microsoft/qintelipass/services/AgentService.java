package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.dtos.AgentDeleteConfirmationDTO;
import org.microsoft.qintelipass.dtos.AgentDeleteResultDTO;
import org.microsoft.qintelipass.dtos.AgentDetailDTO;
import org.microsoft.qintelipass.dtos.AgentListDTO;
import org.microsoft.qintelipass.request.AgentUpdateRequest;

public interface AgentService {
    AgentListDTO listAgents(Long currentUserId, String keyword);

    AgentDetailDTO getAgent(Long currentUserId, Long agentId);

    AgentDetailDTO updateAgent(Long currentUserId, Long agentId, AgentUpdateRequest request);

    AgentDeleteConfirmationDTO getDeleteConfirmation(Long currentUserId, Long agentId);

    AgentDeleteResultDTO deleteAgent(Long currentUserId, Long agentId);

    void requireActiveAgent(Long currentUserId, Long agentId);

    boolean tryRecordActiveAgentCall(
            Long currentUserId,
            Long agentId,
            Long modelId,
            int tokensUsed);
}
