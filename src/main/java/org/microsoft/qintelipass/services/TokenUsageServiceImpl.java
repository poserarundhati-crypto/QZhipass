package org.microsoft.qintelipass.services;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.dtos.TokenUsageRankDTO;
import org.microsoft.qintelipass.dtos.UserTokenUsageDTO;
import org.microsoft.qintelipass.models.*;
import org.microsoft.qintelipass.repository.DailyConfigRepository;
import org.microsoft.qintelipass.repository.ModelsRepository;
import org.microsoft.qintelipass.repository.TokenDailySummaryRepository;
import org.microsoft.qintelipass.repository.TokenUsageLogRepository;
import org.microsoft.qintelipass.repository.UserRepository;
import org.microsoft.qintelipass.util.ExpirationTimeHelper;
import org.microsoft.qintelipass.util.Snowflake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class TokenUsageServiceImpl implements TokenUsageService {
    private static final String USAGE_KEY_PREFIX = "usage:daily:";
    private static final String RANK_KEY_PREFIX = "usage:daily:rank:";
    private static final String LIMIT_KEY_PREFIX = "user:token:limit:";
    private static final String TOTAL_TOKENS_KEY = "models:daily:total:tokens";
    private static final String MODEL_TOTAL_KEY_PREFIX = "models:daily:total:";
    private static long DEFAULT_TOKEN_LIMIT = 100000L;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserService userService;
    private final DailyConfigRepository dailyConfigRepository;
    private final TokenUsageLogRepository tokenUsageLogRepository;
    private final TokenDailySummaryRepository tokenDailySummaryRepository;
    private final ModelsRepository modelsRepository;
    private final UserRepository userRepository;
    private final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    @Autowired
    public TokenUsageServiceImpl(RedisTemplate<String, String> redisTemplate,
                                  UserService userService,
                                  DailyConfigRepository dailyConfigRepository,
                                  TokenUsageLogRepository tokenUsageLogRepository,
                                  TokenDailySummaryRepository tokenDailySummaryRepository,
                                  ModelsRepository modelsRepository,
                                  UserRepository userRepository) {
        this.redisTemplate = redisTemplate;
        this.userService = userService;
        this.dailyConfigRepository = dailyConfigRepository;
        this.tokenUsageLogRepository = tokenUsageLogRepository;
        this.tokenDailySummaryRepository = tokenDailySummaryRepository;
        this.modelsRepository = modelsRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public boolean recordTokenUsage(Long userId, Long modelId, int tokensUsed) {
        if (tokensUsed <= 0 || userId == null) {
            return false;
        }
        return tryRecordTokenUsageWithinLimit(userId, modelId, tokensUsed);
    }

    @Override
    @Transactional
    public boolean tryRecordTokenUsageWithinLimit(Long userId, Long modelId, int tokensUsed) {
        if (userId == null || tokensUsed <= 0 || (modelId != null && modelId <= 0)) {
            throw new IllegalArgumentException("Invalid token usage request");
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new SecurityException("Authenticated user no longer exists"));
        if (!org.microsoft.qintelipass.enums.UserStatus.NORMAL.equals(user.getStatus())) {
            throw new SecurityException("Authenticated user is not active");
        }

        long limit = getUserTokenLimit(userId);
        long currentUsage = getCurrentTokenUsage(userId);
        if (currentUsage >= limit || tokensUsed > limit - currentUsage) {
            log.warn("Token reservation rejected: userId={}, usage={}, requested={}, limit={}",
                    userId, currentUsage, tokensUsed, limit);
            return false;
        }

        return persistTokenUsage(userId, modelId, tokensUsed);
    }

    private boolean persistTokenUsage(Long userId, Long modelId, int tokensUsed) {

        Long effectiveModelId = modelId != null ? modelId : 1L;
        TokenUsageLog logEntry = TokenUsageLog.builder()
                .userId(userId)
                .modelId(effectiveModelId)
                .id(Snowflake.nextId())
                .tokensUsed(tokensUsed)
                .usageDate(LocalDate.now())
                .createdAt(OffsetDateTime.now())
                .build();
        tokenUsageLogRepository.saveAndFlush(logEntry);

        scheduleRedisTelemetry(userId, effectiveModelId, tokensUsed);
        log.debug("Recorded database token usage: userId={}, modelId={}, tokens={}",
                userId, effectiveModelId, tokensUsed);
        return true;
    }

    private void scheduleRedisTelemetry(Long userId, Long modelId, int tokensUsed) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    updateRedisTelemetry(userId, modelId, tokensUsed);
                }
            });
            return;
        }
        updateRedisTelemetry(userId, modelId, tokensUsed);
    }

    private void updateRedisTelemetry(Long userId, Long modelId, int tokensUsed) {
        try {
            String today = getTodayDateString();
            String usageKey = getUsageKey(today, userId);
            String rankKey = getRankKey(today);
            String modelTotalKey = MODEL_TOTAL_KEY_PREFIX + modelId + ":" + today;

            Long currentUsage = redisTemplate.opsForValue().increment(usageKey, tokensUsed);
            if (currentUsage != null && currentUsage == tokensUsed) {
                redisTemplate.expireAt(usageKey, ExpirationTimeHelper.getNextDayTime());
            }

            ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
            zSetOps.incrementScore(rankKey, String.valueOf(userId), tokensUsed);
            Long rankSize = zSetOps.size(rankKey);
            if (rankSize != null && rankSize == 1) {
                redisTemplate.expireAt(rankKey, ExpirationTimeHelper.getNextDayTime());
            }

            redisTemplate.opsForValue().increment(modelTotalKey, tokensUsed);
            redisTemplate.expireAt(modelTotalKey, ExpirationTimeHelper.getNextDayTime());
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, database token log remains authoritative: {}", e.getClass().getSimpleName());
        }
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
        if (topN <= 0) {
            return List.of();
        }
        List<Object[]> usageRows = new ArrayList<>(tokenUsageLogRepository.sumByUserIdForDate(LocalDate.now()));
        usageRows.sort((left, right) -> Long.compare(
                ((Number) right[1]).longValue(),
                ((Number) left[1]).longValue()));
        int rank = 1;
        List<TokenUsageRankDTO> result = new ArrayList<>();
        for (Object[] row : usageRows.stream().limit(topN).toList()) {
            Long userId = ((Number) row[0]).longValue();
            Long totalTokens = ((Number) row[1]).longValue();

            User user = userService.getUserById(userId);
            String userName = user != null ? user.getName() : "Unknown";

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
        return dailyConfigRepository.findByUserId(userId)
                .map(DailyConfig::getDailyLimit)
                .orElse(DEFAULT_TOKEN_LIMIT);
    }

    @Override
    @Transactional
    public void setUserTokenLimit(Long userId, long limit) {
        if (userId == null || limit < 0) {
            throw new IllegalArgumentException("Token limit must be positive");
        }
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("User does not exist"));
        Optional<DailyConfig> existingConfig = dailyConfigRepository.findByUserId(userId);
        if (existingConfig.isPresent()) {
            DailyConfig config = existingConfig.get();
            config.setDailyLimit(limit);
            dailyConfigRepository.saveAndFlush(config);
        } else {
            DailyConfig config = DailyConfig.builder()
                    .userId(userId)
                    .dailyLimit(limit)
                    .build();
            dailyConfigRepository.saveAndFlush(config);
        }

        String limitKey = LIMIT_KEY_PREFIX + userId;
        try {
            redisTemplate.opsForValue().set(limitKey, String.valueOf(limit));
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, database token limit remains authoritative: {}", e.getClass().getSimpleName());
        }

        log.info("Set token limit: userId={}, limit={}", userId, limit);
    }

    @Override
    public String getTodayTotalTokens() {
        return getTodayTotalTokensFromDatabase();
    }

    @Override
    public void increaseDailyTotalTokens(Integer tokens) {
        try {
            this.redisTemplate.opsForValue().increment(TOTAL_TOKENS_KEY, tokens);
            this.redisTemplate.expireAt(TOTAL_TOKENS_KEY, ExpirationTimeHelper.getNextDayTime());
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, daily total will be derived from database logs: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public Long getOveruseUsers() {
        long overuseCount = 0;
        for (Object[] row : tokenUsageLogRepository.sumByUserIdForDate(LocalDate.now())) {
            Long userId = ((Number) row[0]).longValue();
            long usage = ((Number) row[1]).longValue();
            long limit = getUserTokenLimit(userId);
            if (usage >= limit) {
                overuseCount++;
            }
        }
        return overuseCount;
    }

    @Override
    public Long getDailyTokenLimit() {
        return DEFAULT_TOKEN_LIMIT;
    }

    @Override
    public void setDailyTokenLimit(Long value) {
        DEFAULT_TOKEN_LIMIT = value;
    }

    @Override
    public Map<String, Object> getModelStatisticsForLast7Days() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(6);

        List<TokenDailySummary> summaries = tokenDailySummaryRepository.findByUsageDateBetween(startDate, today);

        Map<String, Map<String, Long>> modelDailyStats = new HashMap<>();
        Set<String> modelIds = new HashSet<>();
        List<String> dates = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            dates.add(today.minusDays(6 - i).format(DATE_FORMATTER));
        }

        for (TokenDailySummary summary : summaries) {
            String modelKey = String.valueOf(summary.getModelId());
            modelIds.add(modelKey);
            String dateStr = summary.getUsageDate().format(DATE_FORMATTER);

            modelDailyStats.computeIfAbsent(modelKey, k -> new HashMap<>());
            modelDailyStats.get(modelKey).put(dateStr, summary.getTotalTokens());
        }

        String todayStr = today.format(DATE_FORMATTER);
        for (Object[] row : tokenUsageLogRepository.sumByModelIdForDate(today)) {
            String modelId = Long.toString(((Number) row[0]).longValue());
            long todayTokens = ((Number) row[1]).longValue();
            modelIds.add(modelId);
            modelDailyStats.computeIfAbsent(modelId, key -> new HashMap<>());
            modelDailyStats.get(modelId).put(todayStr, todayTokens);
        }

        List<Map<String, Object>> modelStatsList = new ArrayList<>();
        for (String modelId : modelIds) {
            Map<String, Object> modelStat = new HashMap<>();
            modelStat.put("modelId", modelId);
            modelStat.put("modelName", getModelName(Long.parseLong(modelId)));

            List<Map<String, Object>> dailyUsage = new ArrayList<>();
            for (String date : dates) {
                Map<String, Object> dayStat = new HashMap<>();
                dayStat.put("date", date);
                dayStat.put("tokens", modelDailyStats.getOrDefault(modelId, new HashMap<>()).getOrDefault(date, 0L));
                dailyUsage.add(dayStat);
            }
            modelStat.put("dailyUsage", dailyUsage);
            modelStatsList.add(modelStat);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("models", modelStatsList);
        result.put("dates", dates);

        return result;
    }

    @Override
    public Long getActiveUserCount() {
        LocalDate today = LocalDate.now();
        List<Long> userIds = tokenUsageLogRepository.findDistinctUserIdsByDate(today);
        return (long) userIds.size();
    }

    @Override
    public Map<String, Object> getDepartmentStatistics() {
        LocalDate today = LocalDate.now();
        List<Object[]> userUsageData = tokenUsageLogRepository.sumByUserIdForDate(today);

        Map<String, Long> departmentUsage = new HashMap<>();
        Map<String, Long> departmentUserCount = new HashMap<>();

        for (Object[] row : userUsageData) {
            Long userId = ((Number) row[0]).longValue();
            Long tokens = ((Number) row[1]).longValue();

            User user = userService.getUserById(userId);
            if (user != null) {
                String department = user.getDepartment() != null ? user.getDepartment() : "未分配";
                departmentUsage.merge(department, tokens, Long::sum);
                departmentUserCount.merge(department, 1L, Long::sum);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("departmentUsage", departmentUsage);
        result.put("departmentUserCount", departmentUserCount);

        return result;
    }

    @Override
    public List<Map<String, Object>> getAllUserTokenUsage() {
        LocalDate today = LocalDate.now();
        List<Object[]> userUsageData = tokenUsageLogRepository.sumByUserIdForDate(today);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : userUsageData) {
            Long userId = ((Number) row[0]).longValue();
            Long tokens = ((Number) row[1]).longValue();

            User user = userService.getUserById(userId);
            if (user != null) {
                long limit = getUserTokenLimit(userId);
                Map<String, Object> userStat = new HashMap<>();
                userStat.put("userId", userId.toString());
                userStat.put("userName", user.getName());
                userStat.put("department", user.getDepartment());
                userStat.put("tokensUsed", tokens);
                userStat.put("tokenLimit", limit);
                userStat.put("isExceeded", tokens >= limit);
                result.add(userStat);
            }
        }

        result.sort((a, b) -> Long.compare((Long) b.get("tokensUsed"), (Long) a.get("tokensUsed")));
        return result;
    }

    @Transactional
    public void aggregateDailyData() {
        LocalDate today = LocalDate.now();
        List<Object[]> modelData = tokenUsageLogRepository.sumByModelIdForDate(today);

        for (Object[] row : modelData) {
            Long modelId = ((Number) row[0]).longValue();
            Long totalTokens = ((Number) row[1]).longValue();

            Optional<TokenDailySummary> existingSummary = tokenDailySummaryRepository.findByUsageDateAndModelId(today, modelId);
            if (existingSummary.isPresent()) {
                TokenDailySummary summary = existingSummary.get();
                summary.setTotalTokens(totalTokens);
                tokenDailySummaryRepository.save(summary);
            } else {
                TokenDailySummary summary = TokenDailySummary.builder()
                        .usageDate(today)
                        .modelId(modelId)
                        .totalTokens(totalTokens)
                        .build();
                tokenDailySummaryRepository.save(summary);
            }
        }

        log.info("Daily token usage aggregated for date: {}", today);
    }

    private long getCurrentTokenUsage(Long userId) {
        return getCurrentTokenUsageFromDatabase(userId);
    }

    private long getCurrentTokenUsageFromDatabase(Long userId) {
        return Optional.ofNullable(tokenUsageLogRepository.sumTokensByUserIdForDate(userId, LocalDate.now()))
                .orElse(0L);
    }

    private String getTodayTotalTokensFromDatabase() {
        long total = tokenUsageLogRepository.sumByUserIdForDate(LocalDate.now()).stream()
                .map(row -> (Number) row[1])
                .mapToLong(Number::longValue)
                .sum();
        return Long.toString(total);
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

    private String getModelName(Long modelId) {
        return modelsRepository.findById(modelId)
                .map(Models::getModelName)
                .orElse("Model-" + modelId);
    }
}
