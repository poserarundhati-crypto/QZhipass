package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.dtos.AgentDeleteConfirmationDTO;
import org.microsoft.qintelipass.dtos.AgentDeleteResultDTO;
import org.microsoft.qintelipass.dtos.AgentDetailDTO;
import org.microsoft.qintelipass.dtos.AgentListDTO;
import org.microsoft.qintelipass.dtos.AgentSummaryDTO;
import org.microsoft.qintelipass.dtos.AgentInvocationConfig;
import org.microsoft.qintelipass.dtos.CallableAgentDTO;
import org.microsoft.qintelipass.dtos.CallableAgentListDTO;
import org.microsoft.qintelipass.exceptions.AgentNotFoundException;
import org.microsoft.qintelipass.exceptions.InvalidAgentRequestException;
import org.microsoft.qintelipass.models.Agent;
import org.microsoft.qintelipass.repository.AgentRepository;
import org.microsoft.qintelipass.request.AgentUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AgentServiceImpl implements AgentService {
    private static final int MAX_AGENT_NAME_LENGTH = 128;

    private final AgentRepository agentRepository;
    private final TokenUsageService tokenUsageService;

    public AgentServiceImpl(AgentRepository agentRepository, TokenUsageService tokenUsageService) {
        this.agentRepository = agentRepository;
        this.tokenUsageService = tokenUsageService;
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
    public CallableAgentListDTO listCallableAgents(Long currentUserId, String keyword) {
        requireCurrentUser(currentUserId);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        List<Agent> agents = normalizedKeyword.isEmpty()
                ? agentRepository.findAllByCreatedByAndStatusAndAvailableTrueOrderByAgentNameAsc(
                        currentUserId, Agent.STATUS_ACTIVE)
                : agentRepository
                        .findAllByCreatedByAndStatusAndAvailableTrueAndAgentNameContainingIgnoreCaseOrderByAgentNameAsc(
                                currentUserId, Agent.STATUS_ACTIVE, normalizedKeyword);
        List<CallableAgentDTO> items = agents.stream()
                .filter(this::hasCompleteInvocationData)
                .map(agent -> new CallableAgentDTO(
                        agent.getId().toString(),
                        agent.getAgentName(),
                        true,
                        agent.getBaseModel(),
                        agent.getCreatedBy().toString(),
                        true))
                .toList();
        return new CallableAgentListDTO(items, items.size());
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

        int affectedRows = agentRepository.markDeleted(
                agentId,
                currentUserId,
                Agent.STATUS_ACTIVE,
                Agent.STATUS_DELETED);
        if (affectedRows < 0 || affectedRows > 1) {
            throw new IllegalStateException("Agent删除影响行数异常");
        }

        Agent ownedAgent = agentRepository.findByIdAndCreatedBy(agentId, currentUserId)
                .filter(agent -> Integer.valueOf(Agent.STATUS_DELETED).equals(agent.getStatus()))
                .orElseThrow(AgentNotFoundException::new);
        boolean alreadyDeleted = affectedRows == 0;
        String agentName = alreadyDeleted ? null : ownedAgent.getAgentName();
        return new AgentDeleteResultDTO(agentId.toString(), agentName, true, alreadyDeleted);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireActiveAgent(Long currentUserId, Long agentId) {
        getActiveOwnedAgent(currentUserId, agentId);
    }

    @Override
    @Transactional(readOnly = true)
    public AgentInvocationConfig requireCallableAgent(Long currentUserId, Long agentId) {
        requireCurrentUser(currentUserId);
        validateAgentId(agentId);
        Agent agent = agentRepository
                .findByIdAndCreatedByAndStatusAndAvailableTrue(
                        agentId, currentUserId, Agent.STATUS_ACTIVE)
                .filter(this::hasCompleteInvocationData)
                .orElseThrow(AgentNotFoundException::new);
        return new AgentInvocationConfig(
                agent.getId(),
                agent.getAgentName(),
                agent.getPrompt(),
                agent.getBaseModel());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean tryRecordActiveAgentCall(
            Long currentUserId,
            Long agentId,
            Long modelId,
            int tokensUsed) {
        requireCurrentUser(currentUserId);
        validateAgentId(agentId);
        if (modelId == null || modelId <= 0 || tokensUsed <= 0) {
            throw new InvalidAgentRequestException("modelId或token数量格式无效");
        }

        agentRepository.findActiveOwnedForUpdate(
                        agentId, currentUserId, Agent.STATUS_ACTIVE)
                .orElseThrow(AgentNotFoundException::new);
        return tokenUsageService.tryRecordTokenUsageWithinLimit(
                currentUserId, modelId, tokensUsed);
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

    private boolean hasCompleteInvocationData(Agent agent) {
        return agent != null
                && StringUtils.hasText(agent.getAgentName())
                && StringUtils.hasText(agent.getPrompt())
                && StringUtils.hasText(agent.getBaseModel());
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
