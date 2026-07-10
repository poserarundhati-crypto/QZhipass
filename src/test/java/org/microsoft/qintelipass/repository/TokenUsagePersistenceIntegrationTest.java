package org.microsoft.qintelipass.repository;

import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.models.DailyConfig;
import org.microsoft.qintelipass.models.TokenDailySummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:token-persistence;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class TokenUsagePersistenceIntegrationTest {
    @Autowired
    private DailyConfigRepository dailyConfigRepository;

    @Autowired
    private TokenDailySummaryRepository tokenDailySummaryRepository;

    @Test
    void newDailyConfigBuilderPersistsGeneratedIdAndTimestamp() {
        DailyConfig saved = dailyConfigRepository.saveAndFlush(DailyConfig.builder()
                .userId(101L)
                .dailyLimit(100_000L)
                .build());

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertEquals(100_000L, saved.getDailyLimit());
    }

    @Test
    void newDailySummaryBuilderPersistsGeneratedIdAndTimestamp() {
        TokenDailySummary saved = tokenDailySummaryRepository.saveAndFlush(TokenDailySummary.builder()
                .usageDate(LocalDate.now())
                .modelId(1L)
                .totalTokens(42L)
                .build());

        assertNotNull(saved.getId());
        assertNotNull(saved.getUpdatedAt());
        assertEquals(42L, saved.getTotalTokens());
    }
}
