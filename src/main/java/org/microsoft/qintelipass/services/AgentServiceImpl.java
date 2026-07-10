package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.dtos.AgentDeleteConfirmationDTO;
import org.microsoft.qintelipass.dtos.AgentDeleteResultDTO;
import org.microsoft.qintelipass.dtos.AgentDetailDTO;
import org.microsoft.qintelipass.dtos.AgentListDTO;
import org.microsoft.qintelipass.dtos.AgentSummaryDTO;
import org.microsoft.qintelipass.exceptions.AgentNotFoundException;
import org.microsoft.qintelipass.exceptions.InvalidAgentRequestException;
import org.microsoft.qintelipass.models.Agent;
import org.microsoft.qintelipass.repository.AgentRepository;
import org.microsoft.qintelipass.request.AgentUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AgentServiceImpl implements AgentService {
    private static final int MAX_AGENT_NAME_LENGTH = 128;

    private final AgentRepository agentRepository;

    public AgentServiceImpl(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AgentListDTO listAgents(Long currentUserId, String keyword) {
        requireCurrentUser(currentUserId);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        List<Agent> agents = normalizedKeyword.isEmpty()
                ? agentRepository.findAllByCreatedByAndStatusOrderByAgentNameAsc(
                        currentUserId, Agent.STATUS_ACTIVE)
                : agentRepository.findAllByCreatedByAndStatusAndAgentNameContainingIgnoreCaseOrderByAgentNameAsc(
                        currentUserId, Agent.STATUS_ACTIVE, normalizedKeyword);
        List<AgentSummaryDTO> items = agents.stream()
                .map(agent -> new AgentSummaryDTO(agent.getId().toString(), agent.getAgentName()))
                .toList();
        return new AgentListDTO(items, items.size());
    }

    @Override
    @Transactional(readOnly = true)
    public AgentDetailDTO getAgent(Long currentUserId, Long agentId) {
        Agent agent = getActiveOwnedAgent(currentUserId, agentId);
        return toDetail(agent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentDetailDTO updateAgent(Long currentUserId, Long agentId, AgentUpdateRequest request) {
        requireCurrentUser(currentUserId);
        validateAgentId(agentId);
        String agentName = validateAgentName(request);

        int affectedRows = agentRepository.renameActiveAgent(
                agentId, currentUserId, Agent.STATUS_ACTIVE, agentName);
        if (affectedRows == 1) {
            return getAgent(currentUserId, agentId);
        }

        Agent existing = agentRepository
                .findByIdAndCreatedByAndStatus(agentId, currentUserId, Agent.STATUS_ACTIVE)
                .orElseThrow(AgentNotFoundException::new);
        if (!agentName.equals(existing.getAgentName())) {
            throw new AgentNotFoundException();
        }
        return toDetail(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public AgentDeleteConfirmationDTO getDeleteConfirmation(Long currentUserId, Long agentId) {
        Agent agent = getActiveOwnedAgent(currentUserId, agentId);
        return new AgentDeleteConfirmationDTO(
                agent.getId().toString(),
                agent.getAgentName(),
                true,
                "确定要删除“" + agent.getAgentName() + "”吗？");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentDeleteResultDTO deleteAgent(Long currentUserId, Long agentId) {
        requireCurrentUser(currentUserId);
        validateAgentId(agentId);

        Agent ownedAgent = agentRepository.findByIdAndCreatedBy(agentId, currentUserId)
                .orElseThrow(AgentNotFoundException::new);
        String agentName = ownedAgent.getAgentName();

        if (Agent.STATUS_DELETED == ownedAgent.getStatus()) {
            return deleteResult(ownedAgent, true);
        }
        if (Agent.STATUS_ACTIVE != ownedAgent.getStatus()) {
            throw new AgentNotFoundException();
        }

        int affectedRows = agentRepository.markDeleted(
                agentId,
                currentUserId,
                Agent.STATUS_ACTIVE,
                Agent.STATUS_DELETED);
        if (affectedRows == 1) {
            return new AgentDeleteResultDTO(agentId.toString(), agentName, true, false);
        }

        return new AgentDeleteResultDTO(agentId.toString(), agentName, true, true);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireActiveAgent(Long currentUserId, Long agentId) {
        getActiveOwnedAgent(currentUserId, agentId);
    }

    private Agent getActiveOwnedAgent(Long currentUserId, Long agentId) {
        requireCurrentUser(currentUserId);
        validateAgentId(agentId);
        return agentRepository
                .findByIdAndCreatedByAndStatus(agentId, currentUserId, Agent.STATUS_ACTIVE)
                .orElseThrow(AgentNotFoundException::new);
    }

    private AgentDetailDTO toDetail(Agent agent) {
        return new AgentDetailDTO(agent.getId().toString(), agent.getAgentName());
    }

    private AgentDeleteResultDTO deleteResult(Agent agent, boolean alreadyDeleted) {
        return new AgentDeleteResultDTO(
                agent.getId().toString(),
                agent.getAgentName(),
                true,
                alreadyDeleted);
    }

    private void requireCurrentUser(Long currentUserId) {
        if (currentUserId == null) {
            throw new SecurityException("未登录或登录已失效");
        }
    }

    private void validateAgentId(Long agentId) {
        if (agentId == null || agentId <= 0) {
            throw new InvalidAgentRequestException("agentId格式无效");
        }
    }

    private String validateAgentName(AgentUpdateRequest request) {
        if (request == null || request.agentName() == null || request.agentName().trim().isEmpty()) {
            throw new InvalidAgentRequestException("agentName不能为空");
        }
        String agentName = request.agentName().trim();
        if (agentName.length() > MAX_AGENT_NAME_LENGTH) {
            throw new InvalidAgentRequestException("agentName长度不能超过128个字符");
        }
        return agentName;
    }
}
