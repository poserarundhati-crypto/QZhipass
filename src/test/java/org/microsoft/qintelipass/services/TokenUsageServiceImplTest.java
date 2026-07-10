package org.microsoft.qintelipass.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.microsoft.qintelipass.dtos.TokenUsageRankDTO;
import org.microsoft.qintelipass.models.DailyConfig;
import org.microsoft.qintelipass.models.TokenUsageLog;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.repository.DailyConfigRepository;
import org.microsoft.qintelipass.repository.ModelsRepository;
import org.microsoft.qintelipass.repository.TokenDailySummaryRepository;
import org.microsoft.qintelipass.repository.TokenUsageLogRepository;
import org.microsoft.qintelipass.repository.UserRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenUsageServiceImplTest {
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ZSetOperations<String, String> zSetOperations;
    @Mock
    private UserService userService;
    @Mock
    private DailyConfigRepository dailyConfigRepository;
    @Mock
    private TokenUsageLogRepository tokenUsageLogRepository;
    @Mock
    private TokenDailySummaryRepository tokenDailySummaryRepository;
    @Mock
    private ModelsRepository modelsRepository;
    @Mock
    private UserRepository userRepository;

    private TokenUsageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TokenUsageServiceImpl(
                redisTemplate,
                userService,
                dailyConfigRepository,
                tokenUsageLogRepository,
                tokenDailySummaryRepository,
                modelsRepository,
                userRepository);
    }

    @Test
    void quotaCheckUsesDatabaseEvenWhenRedisUsageKeyIsStale() {
        long userId = 7L;
        when(dailyConfigRepository.findByUserId(userId)).thenReturn(Optional.of(
                DailyConfig.builder().userId(userId).dailyLimit(100000L).build()));
        when(tokenUsageLogRepository.sumTokensByUserIdForDate(userId, LocalDate.now())).thenReturn(100000L);

        assertFalse(service.checkTokenLimit(userId));

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void rankAndTotalAreDerivedFromDatabase() {
        List<Object[]> rows = List.of(
                new Object[]{2L, 25L},
                new Object[]{1L, 50L},
                new Object[]{3L, 10L});
        when(tokenUsageLogRepository.sumByUserIdForDate(LocalDate.now())).thenReturn(rows);
        when(userService.getUserById(1L)).thenReturn(user(1L, "first"));
        when(userService.getUserById(2L)).thenReturn(user(2L, "second"));

        List<TokenUsageRankDTO> rank = service.getDailyTokenRank(2);

        assertEquals(List.of(1L, 2L), rank.stream().map(TokenUsageRankDTO::getUserId).toList());
        assertEquals(List.of(50L, 25L), rank.stream().map(TokenUsageRankDTO::getTotalTokens).toList());
        assertEquals("85", service.getTodayTotalTokens());
        verify(redisTemplate, never()).keys(anyString());
    }

    @Test
    void overuseFallsBackToDatabaseWhenRedisIsUnavailable() {
        long userId = 9L;
        when(tokenUsageLogRepository.sumByUserIdForDate(LocalDate.now()))
                .thenReturn(List.<Object[]>of(new Object[]{userId, 120L}));
        when(dailyConfigRepository.findByUserId(userId)).thenReturn(Optional.of(
                DailyConfig.builder().userId(userId).dailyLimit(100L).build()));

        assertEquals(1L, service.getOveruseUsers());
    }

    @Test
    void databaseFailureDoesNotCreateRedisOnlyUsage() {
        User user = user(1L, "database-failure-user");
        user.setStatus(org.microsoft.qintelipass.enums.UserStatus.NORMAL);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(dailyConfigRepository.findByUserId(1L)).thenReturn(Optional.of(
                DailyConfig.builder().userId(1L).dailyLimit(1000L).build()));
        when(tokenUsageLogRepository.sumTokensByUserIdForDate(1L, LocalDate.now()))
                .thenReturn(0L);
        when(tokenUsageLogRepository.saveAndFlush(any(TokenUsageLog.class)))
                .thenThrow(new IllegalStateException("database write failed"));

        assertThrows(IllegalStateException.class, () -> service.recordTokenUsage(1L, 2L, 100));

        verify(valueOperations, never()).increment(anyString(), any(Long.class));
        verify(redisTemplate, never()).opsForZSet();
    }

    @Test
    void databaseLimitWinsWithoutReadingStaleRedisValue() {
        long userId = 12L;
        when(dailyConfigRepository.findByUserId(userId)).thenReturn(Optional.of(
                DailyConfig.builder().userId(userId).dailyLimit(200L).build()));

        assertEquals(200L, service.getUserTokenLimit(userId));

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void failedLimitWriteDoesNotUpdateRedis() {
        long userId = 13L;
        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user(userId, "limit-user")));
        when(dailyConfigRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(dailyConfigRepository.saveAndFlush(any(DailyConfig.class)))
                .thenThrow(new IllegalStateException("database write failed"));

        assertThrows(IllegalStateException.class, () -> service.setUserTokenLimit(userId, 200L));

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void atomicReservationRejectsPredictedOverageBeforeWriting() {
        long userId = 14L;
        User user = user(userId, "quota-user");
        user.setStatus(org.microsoft.qintelipass.enums.UserStatus.NORMAL);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(dailyConfigRepository.findByUserId(userId)).thenReturn(Optional.of(
                DailyConfig.builder().userId(userId).dailyLimit(100L).build()));
        when(tokenUsageLogRepository.sumTokensByUserIdForDate(userId, LocalDate.now()))
                .thenReturn(95L);

        assertFalse(service.tryRecordTokenUsageWithinLimit(userId, 1L, 10));

        verify(tokenUsageLogRepository, never()).saveAndFlush(any(TokenUsageLog.class));
    }

    @Test
    void atomicReservationPersistsWhenRequestedUsageFits() {
        long userId = 15L;
        User user = user(userId, "quota-user");
        user.setStatus(org.microsoft.qintelipass.enums.UserStatus.NORMAL);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(dailyConfigRepository.findByUserId(userId)).thenReturn(Optional.of(
                DailyConfig.builder().userId(userId).dailyLimit(100L).build()));
        when(tokenUsageLogRepository.sumTokensByUserIdForDate(userId, LocalDate.now()))
                .thenReturn(90L);

        assertTrue(service.tryRecordTokenUsageWithinLimit(userId, 1L, 10));

        verify(tokenUsageLogRepository).saveAndFlush(any(TokenUsageLog.class));
    }

    private User user(long id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        return user;
    }
}
