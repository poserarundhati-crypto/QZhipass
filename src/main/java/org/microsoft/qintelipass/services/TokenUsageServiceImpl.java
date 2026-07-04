package org.microsoft.qintelipass.services;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.dtos.TokenUsageRankDTO;
import org.microsoft.qintelipass.dtos.UserTokenUsageDTO;
import org.microsoft.qintelipass.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TokenUsageServiceImpl implements TokenUsageService {
    private static final String USAGE_KEY_PREFIX = "usage:daily:";
    private static final String RANK_KEY_PREFIX = "usage:daily:rank:";
    private static final String LIMIT_KEY_PREFIX = "user:token:limit:";

    private static final long DEFAULT_TOKEN_LIMIT = 100000L;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private UserService userService;

    private final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public boolean recordTokenUsage(Long userId, int tokensUsed) {
        if (tokensUsed <= 0) {
            return true;
        }

        try {
            String today = getTodayDateString();
            String usageKey = getUsageKey(today, userId);
            String rankKey = getRankKey(today);

            Long currentUsage = redisTemplate.opsForValue().increment(usageKey, tokensUsed);

            if (currentUsage != null && currentUsage == tokensUsed) {
                long ttl = getSecondsUntilMidnight();
                redisTemplate.expire(usageKey, ttl, TimeUnit.SECONDS);
            }

            ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
            zSetOps.incrementScore(rankKey, String.valueOf(userId), tokensUsed);

            if (zSetOps.size(rankKey) != null && zSetOps.size(rankKey) == 1) {
                long ttl = getSecondsUntilMidnight();
                redisTemplate.expire(rankKey, ttl, TimeUnit.SECONDS);
            }

            log.debug("Recorded token usage: userId={}, tokens={}, total={}", userId, tokensUsed, currentUsage);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, token usage was not persisted: {}", e.getMessage());
        }
        return true;
    }

    @Override
    public boolean checkTokenLimit(Long userId) {
        long limit = getUserTokenLimit(userId);
        long currentUsage = getCurrentTokenUsage(userId);
        boolean exceeded = currentUsage >= limit;

        if (exceeded) {
            log.warn("User {} exceeded token limit: usage={}, limit={}", userId, currentUsage, limit);
        }

        return !exceeded;
    }

    @Override
    public UserTokenUsageDTO getUserTokenUsage(Long userId) {
        User user = userService.getUserById(userId);
        String userName = user != null ? user.getName() : "Unknown";

        long currentUsage = getCurrentTokenUsage(userId);
        long limit = getUserTokenLimit(userId);

        return UserTokenUsageDTO.builder()
                .userId(userId)
                .userName(userName)
                .tokenUsed(currentUsage)
                .tokenLimit(limit)
                .isExceeded(currentUsage >= limit)
                .build();
    }

    @Override
    public List<TokenUsageRankDTO> getDailyTokenRank(int topN) {
        String today = getTodayDateString();
        String rankKey = getRankKey(today);

        Set<ZSetOperations.TypedTuple<String>> tuples;
        try {
            tuples = redisTemplate.opsForZSet()
                    .reverseRangeWithScores(rankKey, 0, topN - 1);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, returning empty token rank: {}", e.getMessage());
            return List.of();
        }

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        int rank = 1;
        List<TokenUsageRankDTO> result = List.of();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long userId = Long.valueOf(tuple.getValue());
            Double score = tuple.getScore();
            Long totalTokens = score != null ? score.longValue() : 0L;

            User user = userService.getUserById(userId);
            String userName = user != null ? user.getName() : "Unknown";

            result = new java.util.ArrayList<>(result);
            result.add(TokenUsageRankDTO.builder()
                    .userId(userId)
                    .userName(userName)
                    .totalTokens(totalTokens)
                    .rank(rank++)
                    .build());
        }

        return result;
    }

    @Override
    public long getUserTokenLimit(Long userId) {
        String limitKey = LIMIT_KEY_PREFIX + userId;
        String limitStr;
        try {
            limitStr = redisTemplate.opsForValue().get(limitKey);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, using default token limit: {}", e.getMessage());
            return DEFAULT_TOKEN_LIMIT;
        }

        if (limitStr != null) {
            try {
                return Long.parseLong(limitStr);
            } catch (NumberFormatException e) {
                log.error("Invalid token limit format for user: {}", userId);
            }
        }

        return DEFAULT_TOKEN_LIMIT;
    }

    @Override
    public void setUserTokenLimit(Long userId, long limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Token limit must be positive");
        }
        String limitKey = LIMIT_KEY_PREFIX + userId;
        try {
            redisTemplate.opsForValue().set(limitKey, String.valueOf(limit));
            log.info("Set token limit: userId={}, limit={}", userId, limit);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, token limit was not persisted: {}", e.getMessage());
        }
    }

    private long getCurrentTokenUsage(Long userId) {
        String today = getTodayDateString();
        String usageKey = getUsageKey(today, userId);
        String usageStr;
        try {
            usageStr = redisTemplate.opsForValue().get(usageKey);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, using zero token usage: {}", e.getMessage());
            return 0L;
        }

        if (usageStr == null) {
            return 0L;
        }

        try {
            return Long.parseLong(usageStr);
        } catch (NumberFormatException e) {
            log.error("Invalid token usage format for user: {}", userId);
            return 0L;
        }
    }

    private String getTodayDateString() {
        return LocalDate.now().format(DATE_FORMATTER);
    }

    private String getUsageKey(String date, Long userId) {
        return USAGE_KEY_PREFIX + date + ":" + userId;
    }

    private String getRankKey(String date) {
        return RANK_KEY_PREFIX + date;
    }

    private long getSecondsUntilMidnight() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        java.time.LocalDateTime midnight = tomorrow.atStartOfDay();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        return java.time.Duration.between(now, midnight).getSeconds();
    }
}
