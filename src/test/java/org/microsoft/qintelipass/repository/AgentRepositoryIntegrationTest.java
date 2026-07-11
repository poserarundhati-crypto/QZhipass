package org.microsoft.qintelipass.repository;

import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.models.Agent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:agent-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
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
class AgentRepositoryIntegrationTest {
    private static final long OWNER_ID = 101L;
    private static final long OTHER_OWNER_ID = 202L;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void markDeletedRequiresMatchingOwnerAndActiveStatus() {
        Agent ownedActive = persistAgent(1001L, OWNER_ID, "Owned Active", Agent.STATUS_ACTIVE);
        Agent otherOwnedActive = persistAgent(1002L, OTHER_OWNER_ID, "Other Owner Active", Agent.STATUS_ACTIVE);

        int otherUserAffected = agentRepository.markDeleted(
                ownedActive.getId(), OTHER_OWNER_ID, Agent.STATUS_ACTIVE, Agent.STATUS_DELETED);

        assertEquals(0, otherUserAffected);
        assertEquals(
                Agent.STATUS_ACTIVE,
                agentRepository.findById(ownedActive.getId()).orElseThrow().getStatus().intValue());

        int firstAffected = agentRepository.markDeleted(
                ownedActive.getId(), OWNER_ID, Agent.STATUS_ACTIVE, Agent.STATUS_DELETED);

        assertEquals(1, firstAffected);
        assertEquals(
                Agent.STATUS_DELETED,
                agentRepository.findById(ownedActive.getId()).orElseThrow().getStatus().intValue());

        int secondAffected = agentRepository.markDeleted(
                ownedActive.getId(), OWNER_ID, Agent.STATUS_ACTIVE, Agent.STATUS_DELETED);
        int foreignAgentAffected = agentRepository.markDeleted(
                otherOwnedActive.getId(), OWNER_ID, Agent.STATUS_ACTIVE, Agent.STATUS_DELETED);

        assertEquals(0, secondAffected);
        assertEquals(0, foreignAgentAffected);
        assertEquals(
                Agent.STATUS_ACTIVE,
                agentRepository.findById(otherOwnedActive.getId()).orElseThrow().getStatus().intValue());
    }

    @Test
    void activeListAndKeywordSearchExcludeDeletedAndOtherOwnersAgents() {
        Agent alpha = persistAgent(2001L, OWNER_ID, "Alpha Analyst", Agent.STATUS_ACTIVE);
        Agent beta = persistAgent(2002L, OWNER_ID, "Beta Writer", Agent.STATUS_ACTIVE);
        persistAgent(2003L, OWNER_ID, "Alpha Deleted", Agent.STATUS_DELETED);
        persistAgent(2004L, OTHER_OWNER_ID, "Alpha Other Owner", Agent.STATUS_ACTIVE);

        List<Long> activeIds = agentRepository
                .findAllByCreatedByAndStatusOrderByAgentNameAsc(OWNER_ID, Agent.STATUS_ACTIVE)
                .stream()
                .map(Agent::getId)
                .toList();
        List<Long> keywordIds = agentRepository
                .findAllByCreatedByAndStatusAndAgentNameContainingIgnoreCaseOrderByAgentNameAsc(
                        OWNER_ID, Agent.STATUS_ACTIVE, "ALPHA")
                .stream()
                .map(Agent::getId)
                .toList();

        assertEquals(List.of(alpha.getId(), beta.getId()), activeIds);
        assertEquals(List.of(alpha.getId()), keywordIds);
    }

    @Test
    void callableQueriesRequireOwnerActiveStatusAndAvailableFlag() {
        Agent callable = persistAgent(2101L, OWNER_ID, "Callable Writer", Agent.STATUS_ACTIVE);
        callable.setPrompt("Use a numbered answer");
        callable.setBaseModel("qwen3");
        callable.setAvailable(true);

        Agent disabled = persistAgent(2102L, OWNER_ID, "Disabled Writer", Agent.STATUS_ACTIVE);
        disabled.setPrompt("Disabled");
        disabled.setBaseModel("qwen3");
        disabled.setAvailable(false);

        Agent deleted = persistAgent(2103L, OWNER_ID, "Deleted Writer", Agent.STATUS_DELETED);
        deleted.setPrompt("Deleted");
        deleted.setBaseModel("qwen3");

        Agent foreign = persistAgent(2104L, OTHER_OWNER_ID, "Foreign Writer", Agent.STATUS_ACTIVE);
        foreign.setPrompt("Foreign");
        foreign.setBaseModel("qwen3");
        agentRepository.flush();

        List<Long> callableIds = agentRepository
                .findAllByCreatedByAndStatusAndAvailableTrueOrderByAgentNameAsc(
                        OWNER_ID, Agent.STATUS_ACTIVE)
                .stream()
                .map(Agent::getId)
                .toList();

        assertEquals(List.of(callable.getId()), callableIds);
        assertTrue(agentRepository.findByIdAndCreatedByAndStatusAndAvailableTrue(
                disabled.getId(), OWNER_ID, Agent.STATUS_ACTIVE).isEmpty());
        assertTrue(agentRepository.findByIdAndCreatedByAndStatusAndAvailableTrue(
                deleted.getId(), OWNER_ID, Agent.STATUS_ACTIVE).isEmpty());
        assertTrue(agentRepository.findByIdAndCreatedByAndStatusAndAvailableTrue(
                foreign.getId(), OWNER_ID, Agent.STATUS_ACTIVE).isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentConditionalDeletesChangeExactlyOneRowWithoutErrors() throws Exception {
        long agentId = 3001L;
        int workerCount = 8;
        persistAgent(agentId, OWNER_ID, "Concurrent Delete", Agent.STATUS_ACTIVE);

        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < workerCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start concurrent delete");
                    }
                    return new TransactionTemplate(transactionManager).execute(status ->
                            agentRepository.markDeleted(
                                    agentId,
                                    OWNER_ID,
                                    Agent.STATUS_ACTIVE,
                                    Agent.STATUS_DELETED));
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<Integer> affectedRows = new ArrayList<>();
            for (Future<Integer> future : futures) {
                affectedRows.add(future.get(15, TimeUnit.SECONDS));
            }

            assertEquals(1, affectedRows.stream().filter(value -> value == 1).count());
            assertEquals(workerCount - 1L, affectedRows.stream().filter(value -> value == 0).count());
            assertEquals(1, affectedRows.stream().mapToInt(Integer::intValue).sum());
            assertEquals(
                    Agent.STATUS_DELETED,
                    agentRepository.findById(agentId).orElseThrow().getStatus().intValue());
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            agentRepository.deleteById(agentId);
        }
    }

    private Agent persistAgent(Long id, Long createdBy, String agentName, int status) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setCreatedBy(createdBy);
        agent.setAgentName(agentName);
        agent.setStatus(status);
        return agentRepository.saveAndFlush(agent);
    }
}
