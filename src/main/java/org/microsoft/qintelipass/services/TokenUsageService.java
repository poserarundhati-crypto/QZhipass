package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.dtos.TokenUsageRankDTO;
import org.microsoft.qintelipass.dtos.UserTokenUsageDTO;

import java.util.List;
import java.util.Map;

public interface TokenUsageService {
    boolean recordTokenUsage(Long userId, Long modelId, int tokensUsed);
    boolean tryRecordTokenUsageWithinLimit(Long userId, Long modelId, int tokensUsed);
    boolean checkTokenLimit(Long userId);
    UserTokenUsageDTO getUserTokenUsage(Long userId);
    List<TokenUsageRankDTO> getDailyTokenRank(int topN);
    long getUserTokenLimit(Long userId);
    void setUserTokenLimit(Long userId, long limit);
    String getTodayTotalTokens();
    void increaseDailyTotalTokens(Integer tokens);
    Long getOveruseUsers();
    Long getDailyTokenLimit();
    void setDailyTokenLimit(Long value);
    Map<String, Object> getModelStatisticsForLast7Days();
    void aggregateDailyData();
    Long getActiveUserCount();
    Map<String, Object> getDepartmentStatistics();
    List<Map<String, Object>> getAllUserTokenUsage();
}
