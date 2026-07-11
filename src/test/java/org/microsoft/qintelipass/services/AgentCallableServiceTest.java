package org.microsoft.qintelipass.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.dtos.CallableAgentDTO;
import org.microsoft.qintelipass.dtos.CallableAgentListDTO;
import org.microsoft.qintelipass.exceptions.AgentNotFoundException;
import org.microsoft.qintelipass.models.Agent;
import org.microsoft.qintelipass.repository.AgentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

@DataJpaTest(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:agent-callable-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.show-sql=false"
        },
        showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AgentCallableServiceTest {
    private static final long CURRENT_USER_ID = 101L;
    private static final long OTHER_USER_ID = 202L;

    @Autowired
    private AgentRepository agentRepository;

    private AgentServiceImpl agentService;

    @BeforeEach
    void setUp() {
        agentService = new AgentServiceImpl(agentRepository, mock(TokenUsageService.class));
    }

    @Test
    void callableListContainsOnlyCompleteActiveAvailableOwnedAgents() {
        Agent callable = persistAgent(
                1001L,
                CURRENT_USER_ID,
                "Weekly Writer",
                Agent.STATUS_ACTIVE,
                true,
                "Answer as a numbered weekly report.",
                "qwen3");
        persistAgent(
                1002L,
                CURRENT_USER_ID,
                "Disabled Agent",
                Agent.STATUS_ACTIVE,
                false,
                "Valid prompt",
                "qwen3");
        persistAgent(
                1003L,
                CURRENT_USER_ID,
                "Deleted Agent",
                Agent.STATUS_DELETED,
                true,
                "Valid prompt",
                "qwen3");
        persistAgent(
                1004L,
                OTHER_USER_ID,
                "Other User Agent",
                Agent.STATUS_ACTIVE,
                true,
                "Valid prompt",
                "qwen3");
        persistAgent(
                1005L,
                CURRENT_USER_ID,
                "Missing Prompt",
                Agent.STATUS_ACTIVE,
                true,
                "   ",
                "qwen3");
        persistAgent(
                1006L,
                CURRENT_USER_ID,
                "Missing Model",
                Agent.STATUS_ACTIVE,
                true,
                "Valid prompt",
                "   ");
        persistAgent(
                1007L,
                CURRENT_USER_ID,
                "   ",
                Agent.STATUS_ACTIVE,
                true,
                "Valid prompt",
                "qwen3");

        CallableAgentListDTO result = agentService.listCallableAgents(CURRENT_USER_ID, null);

        assertEquals(1, result.total());
        assertEquals(1, result.items().size());
        CallableAgentDTO item = result.items().getFirst();
        assertAll(
                () -> assertEquals(callable.getId().toString(), item.agentId()),
                () -> assertEquals(callable.getAgentName(), item.agentName()),
                () -> assertEquals(callable.getBaseModel(), item.baseModel()),
                () -> assertEquals(Long.toString(CURRENT_USER_ID), item.createdBy()),
                () -> assertEquals(true, item.available()),
                () -> assertEquals(true, item.allowed()));
    }

    @Test
    void callableKeywordSearchTrimsInputAndKeepsAllCallableConstraints() {
        Agent weekly = persistAgent(
                2001L,
                CURRENT_USER_ID,
                "Weekly Writer",
                Agent.STATUS_ACTIVE,
                true,
                "Valid prompt",
                "qwen3");
        persistAgent(
                2002L,
                CURRENT_USER_ID,
                "Contract Reviewer",
                Agent.STATUS_ACTIVE,
                true,
                "Valid prompt",
                "qwen3");
        persistAgent(
                2003L,
                CURRENT_USER_ID,
                "Weekly Disabled",
                Agent.STATUS_ACTIVE,
                false,
                "Valid prompt",
                "qwen3");
        persistAgent(
                2004L,
                OTHER_USER_ID,
                "Weekly Other User",
                Agent.STATUS_ACTIVE,
                true,
                "Valid prompt",
                "qwen3");

        CallableAgentListDTO result = agentService.listCallableAgents(
                CURRENT_USER_ID,
                "  WEEKLY  ");

        assertEquals(1, result.total());
        assertEquals(List.of(weekly.getId().toString()), result.items().stream()
                .map(CallableAgentDTO::agentId)
                .toList());
    }

    @Test
    void disabledDeletedAndUnownedAgentsCannotBeDirectlyInvoked() {
        Agent disabled = persistAgent(
                3001L,
                CURRENT_USER_ID,
                "Disabled Agent",
                Agent.STATUS_ACTIVE,
                false,
                "Valid prompt",
                "qwen3");
        Agent deleted = persistAgent(
                3002L,
                CURRENT_USER_ID,
                "Deleted Agent",
                Agent.STATUS_DELETED,
                true,
                "Valid prompt",
                "qwen3");
        Agent unowned = persistAgent(
                3003L,
                OTHER_USER_ID,
                "Other User Agent",
                Agent.STATUS_ACTIVE,
                true,
                "Valid prompt",
                "qwen3");

        assertAll(
                () -> assertThrows(
                        AgentNotFoundException.class,
                        () -> agentService.requireCallableAgent(CURRENT_USER_ID, disabled.getId())),
                () -> assertThrows(
                        AgentNotFoundException.class,
                        () -> agentService.requireCallableAgent(CURRENT_USER_ID, deleted.getId())),
                () -> assertThrows(
                        AgentNotFoundException.class,
                        () -> agentService.requireCallableAgent(CURRENT_USER_ID, unowned.getId())));
    }

    private Agent persistAgent(
            long id,
            long createdBy,
            String name,
            int status,
            boolean available,
            String prompt,
            String baseModel) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setCreatedBy(createdBy);
        agent.setAgentName(name);
        agent.setStatus(status);
        agent.setAvailable(available);
        agent.setPrompt(prompt);
        agent.setBaseModel(baseModel);
        return agentRepository.saveAndFlush(agent);
    }
}
