package org.microsoft.qintelipass;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.microsoft.qintelipass.services.AgentServiceImpl;
import org.microsoft.qintelipass.services.TokenUsageService;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {
    private static final long CURRENT_USER_ID = 101L;
    private static final long OTHER_USER_ID = 202L;
    private static final long AGENT_ID = 9001L;
    private static final String AGENT_NAME = "合同审查助手";

    @Mock
    private AgentRepository agentRepository;
    @Mock
    private TokenUsageService tokenUsageService;

    private AgentServiceImpl agentService;

    @BeforeEach
    void setUp() {
        agentService = new AgentServiceImpl(agentRepository, tokenUsageService);
    }

    @Test
    void getDeleteConfirmationReturnsOwnedActiveAgentNameAndMessage() {
        Agent agent = agent(AGENT_ID, CURRENT_USER_ID, AGENT_NAME, Agent.STATUS_ACTIVE);
        when(agentRepository.findByIdAndCreatedByAndStatus(
                AGENT_ID, CURRENT_USER_ID, Agent.STATUS_ACTIVE))
                .thenReturn(Optional.of(agent));

        AgentDeleteConfirmationDTO confirmation =
                agentService.getDeleteConfirmation(CURRENT_USER_ID, AGENT_ID);

        assertAll(
                () -> assertEquals("9001", confirmation.agentId()),
                () -> assertEquals(AGENT_NAME, confirmation.agentName()),
                () -> assertTrue(confirmation.deletable()),
                () -> assertEquals("确定要删除“合同审查助手”吗？", confirmation.confirmationMessage()));
        verify(agentRepository).findByIdAndCreatedByAndStatus(
                AGENT_ID, CURRENT_USER_ID, Agent.STATUS_ACTIVE);
    }

    @Test
    void deleteAgentMarksOwnedActiveAgentDeletedWhenOneRowIsAffected() {
        Agent agent = agent(AGENT_ID, CURRENT_USER_ID, AGENT_NAME, Agent.STATUS_DELETED);
        when(agentRepository.findByIdAndCreatedBy(AGENT_ID, CURRENT_USER_ID))
                .thenReturn(Optional.of(agent));
        when(agentRepository.markDeleted(
                AGENT_ID, CURRENT_USER_ID, Agent.STATUS_ACTIVE, Agent.STATUS_DELETED))
                .thenReturn(1);

        AgentDeleteResultDTO result = agentService.deleteAgent(CURRENT_USER_ID, AGENT_ID);

        assertAll(
                () -> assertEquals("9001", result.agentId()),
                () -> assertEquals(AGENT_NAME, result.agentName()),
                () -> assertTrue(result.deleted()),
                () -> assertFalse(result.alreadyDeleted()));
        InOrder order = inOrder(agentRepository);
        order.verify(agentRepository).markDeleted(
                AGENT_ID, CURRENT_USER_ID, Agent.STATUS_ACTIVE, Agent.STATUS_DELETED);
        order.verify(agentRepository).findByIdAndCreatedBy(AGENT_ID, CURRENT_USER_ID);
    }

    @Test
    void deleteAgentReturnsAlreadyDeletedWithoutLeakingDeletedAgentName() {
        Agent deletedAgent = agent(AGENT_ID, CURRENT_USER_ID, AGENT_NAME, Agent.STATUS_DELETED);
        when(agentRepository.findByIdAndCreatedBy(AGENT_ID, CURRENT_USER_ID))
                .thenReturn(Optional.of(deletedAgent));

        AgentDeleteResultDTO result = agentService.deleteAgent(CURRENT_USER_ID, AGENT_ID);

        assertAll(
                () -> assertTrue(result.deleted()),
                () -> assertTrue(result.alreadyDeleted()),
                () -> assertEquals(null, result.agentName()));
        verify(agentRepository).markDeleted(
                AGENT_ID, CURRENT_USER_ID, Agent.STATUS_ACTIVE, Agent.STATUS_DELETED);
    }

    @Test
    void deleteAgentTreatsZeroAffectedRowsAsConcurrentAlreadyDeletedResult() {
        Agent deletedAgent = agent(AGENT_ID, CURRENT_USER_ID, AGENT_NAME, Agent.STATUS_DELETED);
        when(agentRepository.findByIdAndCreatedBy(AGENT_ID, CURRENT_USER_ID))
                .thenReturn(Optional.of(deletedAgent));
        when(agentRepository.markDeleted(
                AGENT_ID, CURRENT_USER_ID, Agent.STATUS_ACTIVE, Agent.STATUS_DELETED))
                .thenReturn(0);

        AgentDeleteResultDTO result = agentService.deleteAgent(CURRENT_USER_ID, AGENT_ID);

        assertAll(
                () -> assertTrue(result.deleted()),
                () -> assertTrue(result.alreadyDeleted()),
                () -> assertEquals("9001", result.agentId()),
                () -> assertEquals(null, result.agentName()));
        verify(agentRepository).markDeleted(
                AGENT_ID, CURRENT_USER_ID, Agent.STATUS_ACTIVE, Agent.STATUS_DELETED);
    }

    @Test
    void deleteAgentRejectsZeroAffectedRowsWhenOwnedAgentIsNotDeleted() {
        Agent inactiveAgent = agent(AGENT_ID, CURRENT_USER_ID, AGENT_NAME, 2);
        when(agentRepository.markDeleted(
                AGENT_ID, CURRENT_USER_ID, Agent.STATUS_ACTIVE, Agent.STATUS_DELETED))
                .thenReturn(0);
        when(agentRepository.findByIdAndCreatedBy(AGENT_ID, CURRENT_USER_ID))
                .thenReturn(Optional.of(inactiveAgent));

        assertThrows(
                AgentNotFoundException.class,
                () -> agentService.deleteAgent(CURRENT_USER_ID, AGENT_ID));
    }

    @Test
    void deleteAgentUsesSameNotFoundResultForMissingAndOtherUsersAgent() {
        long missingAgentId = 111L;
        long otherUsersAgentId = 222L;
        when(agentRepository.findByIdAndCreatedBy(missingAgentId, CURRENT_USER_ID))
                .thenReturn(Optional.empty());
        when(agentRepository.findByIdAndCreatedBy(otherUsersAgentId, CURRENT_USER_ID))
                .thenReturn(Optional.empty());

        AgentNotFoundException missing = assertThrows(
                AgentNotFoundException.class,
                () -> agentService.deleteAgent(CURRENT_USER_ID, missingAgentId));
        AgentNotFoundException otherUsers = assertThrows(
                AgentNotFoundException.class,
                () -> agentService.deleteAgent(CURRENT_USER_ID, otherUsersAgentId));

        assertAll(
                () -> assertEquals("Agent不存在或无权操作", missing.getMessage()),
                () -> assertEquals(missing.getMessage(), otherUsers.getMessage()));
        verify(agentRepository, never()).findById(anyLong());
        verify(agentRepository, org.mockito.Mockito.times(2))
                .markDeleted(anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void invalidAgentIdsFailBeforeRepositoryAccess() {
        assertAll(
                () -> assertThrows(
                        InvalidAgentRequestException.class,
                        () -> agentService.deleteAgent(CURRENT_USER_ID, null)),
                () -> assertThrows(
                        InvalidAgentRequestException.class,
                        () -> agentService.deleteAgent(CURRENT_USER_ID, 0L)),
                () -> assertThrows(
                        InvalidAgentRequestException.class,
                        () -> agentService.getDeleteConfirmation(CURRENT_USER_ID, -1L)));
        verifyNoInteractions(agentRepository);
    }

    @Test
    void listAgentsWithoutKeywordQueriesOnlyOwnedActiveAgents() {
        Agent first = agent(1L, CURRENT_USER_ID, "财务助手", Agent.STATUS_ACTIVE);
        Agent second = agent(2L, CURRENT_USER_ID, "合同助手", Agent.STATUS_ACTIVE);
        when(agentRepository.findAllByCreatedByAndStatusOrderByAgentNameAsc(
                CURRENT_USER_ID, Agent.STATUS_ACTIVE))
                .thenReturn(List.of(first, second));

        AgentListDTO result = agentService.listAgents(CURRENT_USER_ID, "   ");

        assertAll(
                () -> assertEquals(2, result.total()),
                () -> assertEquals(List.of("1", "2"), result.items().stream()
                        .map(AgentSummaryDTO::agentId)
                        .toList()));
        verify(agentRepository).findAllByCreatedByAndStatusOrderByAgentNameAsc(
                CURRENT_USER_ID, Agent.STATUS_ACTIVE);
        verify(agentRepository, never())
                .findAllByCreatedByAndStatusAndAgentNameContainingIgnoreCaseOrderByAgentNameAsc(
                        anyLong(), anyInt(), anyString());
    }

    @Test
    void listAgentsWithKeywordTrimsItAndQueriesOnlyOwnedActiveAgents() {
        Agent matching = agent(3L, CURRENT_USER_ID, AGENT_NAME, Agent.STATUS_ACTIVE);
        when(agentRepository
                .findAllByCreatedByAndStatusAndAgentNameContainingIgnoreCaseOrderByAgentNameAsc(
                        CURRENT_USER_ID, Agent.STATUS_ACTIVE, "合同"))
                .thenReturn(List.of(matching));

        AgentListDTO result = agentService.listAgents(CURRENT_USER_ID, "  合同  ");

        assertAll(
                () -> assertEquals(1, result.total()),
                () -> assertEquals(AGENT_NAME, result.items().getFirst().agentName()));
        verify(agentRepository)
                .findAllByCreatedByAndStatusAndAgentNameContainingIgnoreCaseOrderByAgentNameAsc(
                        CURRENT_USER_ID, Agent.STATUS_ACTIVE, "合同");
        verify(agentRepository, never())
                .findAllByCreatedByAndStatusOrderByAgentNameAsc(anyLong(), anyInt());
    }

    @Test
    void deletedAgentCannotBeReadOrUsedForAgentCall() {
        when(agentRepository.findByIdAndCreatedByAndStatus(
                AGENT_ID, CURRENT_USER_ID, Agent.STATUS_ACTIVE))
                .thenReturn(Optional.empty());

        AgentNotFoundException detailFailure = assertThrows(
                AgentNotFoundException.class,
                () -> agentService.getAgent(CURRENT_USER_ID, AGENT_ID));
        AgentNotFoundException callFailure = assertThrows(
                AgentNotFoundException.class,
                () -> agentService.requireActiveAgent(CURRENT_USER_ID, AGENT_ID));

        assertAll(
                () -> assertEquals("Agent不存在或无权操作", detailFailure.getMessage()),
                () -> assertEquals(detailFailure.getMessage(), callFailure.getMessage()));
        verify(agentRepository, org.mockito.Mockito.times(2))
                .findByIdAndCreatedByAndStatus(AGENT_ID, CURRENT_USER_ID, Agent.STATUS_ACTIVE);
    }

    @Test
    void activeAgentCallLocksOwnedAgentBeforeAtomicallyReservingQuota() {
        Agent agent = agent(AGENT_ID, CURRENT_USER_ID, AGENT_NAME, Agent.STATUS_ACTIVE);
        when(agentRepository.findActiveOwnedForUpdate(
                AGENT_ID, CURRENT_USER_ID, Agent.STATUS_ACTIVE))
                .thenReturn(Optional.of(agent));
        when(tokenUsageService.tryRecordTokenUsageWithinLimit(
                CURRENT_USER_ID, 1L, 10003)).thenReturn(true);

        assertTrue(agentService.tryRecordActiveAgentCall(
                CURRENT_USER_ID, AGENT_ID, 1L, 10003));

        InOrder inOrder = inOrder(agentRepository, tokenUsageService);
        inOrder.verify(agentRepository).findActiveOwnedForUpdate(
                AGENT_ID, CURRENT_USER_ID, Agent.STATUS_ACTIVE);
        inOrder.verify(tokenUsageService).tryRecordTokenUsageWithinLimit(
                CURRENT_USER_ID, 1L, 10003);
    }

    @Test
    void deletedAgentCannotReserveCallQuota() {
        when(agentRepository.findActiveOwnedForUpdate(
                AGENT_ID, CURRENT_USER_ID, Agent.STATUS_ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(AgentNotFoundException.class, () ->
                agentService.tryRecordActiveAgentCall(
                        CURRENT_USER_ID, AGENT_ID, 1L, 10003));

        verifyNoInteractions(tokenUsageService);
    }

    @Test
    void updateAgentFailsWhenConditionalUpdateCannotFindAnActiveOwnedAgent() {
        AgentUpdateRequest request = new AgentUpdateRequest("新名称");
        when(agentRepository.renameActiveAgent(
                AGENT_ID, CURRENT_USER_ID, Agent.STATUS_ACTIVE, "新名称"))
                .thenReturn(0);
        when(agentRepository.findByIdAndCreatedByAndStatus(
                AGENT_ID, CURRENT_USER_ID, Agent.STATUS_ACTIVE))
                .thenReturn(Optional.empty());

        AgentNotFoundException failure = assertThrows(
                AgentNotFoundException.class,
                () -> agentService.updateAgent(CURRENT_USER_ID, AGENT_ID, request));

        assertEquals("Agent不存在或无权操作", failure.getMessage());
        verify(agentRepository).renameActiveAgent(
                AGENT_ID, CURRENT_USER_ID, Agent.STATUS_ACTIVE, "新名称");
    }

    @Test
    void databaseExceptionDuringDeleteIsPropagated() {
        DataAccessResourceFailureException databaseFailure =
                new DataAccessResourceFailureException("database unavailable");
        when(agentRepository.markDeleted(
                AGENT_ID, CURRENT_USER_ID, Agent.STATUS_ACTIVE, Agent.STATUS_DELETED))
                .thenThrow(databaseFailure);

        DataAccessResourceFailureException thrown = assertThrows(
                DataAccessResourceFailureException.class,
                () -> agentService.deleteAgent(CURRENT_USER_ID, AGENT_ID));

        assertSame(databaseFailure, thrown);
    }

    @Test
    void agentResponseDtosContainOnlyExpectedNonSensitiveFields() {
        assertAll(
                () -> assertEquals(
                        Set.of("agentId", "agentName", "deletable", "confirmationMessage"),
                        recordComponentNames(AgentDeleteConfirmationDTO.class)),
                () -> assertEquals(
                        Set.of("agentId", "agentName", "deleted", "alreadyDeleted"),
                        recordComponentNames(AgentDeleteResultDTO.class)),
                () -> assertEquals(
                        Set.of("agentId", "agentName"),
                        recordComponentNames(AgentDetailDTO.class)),
                () -> assertEquals(
                        Set.of("items", "total"),
                        recordComponentNames(AgentListDTO.class)),
                () -> assertEquals(
                        Set.of("agentId", "agentName"),
                        recordComponentNames(AgentSummaryDTO.class)));
    }

    @Test
    void missingCurrentUserFailsBeforeRepositoryAccess() {
        SecurityException failure = assertThrows(
                SecurityException.class,
                () -> agentService.getDeleteConfirmation(null, AGENT_ID));

        assertEquals("未登录或登录已失效", failure.getMessage());
        verifyNoInteractions(agentRepository);
    }

    private Agent agent(long id, long createdBy, String name, int status) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setCreatedBy(createdBy);
        agent.setAgentName(name);
        agent.setStatus(status);
        return agent;
    }

    private Set<String> recordComponentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());
    }
}
