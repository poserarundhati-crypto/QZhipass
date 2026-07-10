package org.microsoft.qintelipass.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.DailyConfig;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.services.TokenUsageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:token-quota;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class TokenUsageQuotaIntegrationTest {
    @Autowired private TokenUsageService tokenUsageService;
    @Autowired private UserRepository userRepository;
    @Autowired private DailyConfigRepository dailyConfigRepository;
    @Autowired private TokenUsageLogRepository tokenUsageLogRepository;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    private User user;

    @BeforeEach
    void setUp() {
        tokenUsageLogRepository.deleteAll();
        dailyConfigRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setName("quota-owner");
        user.setPhone("13800000009");
        user.setPasswordHash("unused");
        user.setStatus(UserStatus.NORMAL);
        user = userRepository.saveAndFlush(user);

        dailyConfigRepository.saveAndFlush(DailyConfig.builder()
                .userId(user.getId())
                .dailyLimit(24L)
                .build());
    }

    @AfterEach
    void tearDown() {
        tokenUsageLogRepository.deleteAll();
        dailyConfigRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void concurrentReservationsNeverExceedDatabaseLimit() throws Exception {
        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("quota test start timed out");
                    }
                    return tokenUsageService.tryRecordTokenUsageWithinLimit(
                            user.getId(), 1L, 6);
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int accepted = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(15, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }

            assertEquals(4, accepted);
            assertEquals(24L, tokenUsageLogRepository.sumTokensByUserIdForDate(
                    user.getId(), LocalDate.now()));
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
