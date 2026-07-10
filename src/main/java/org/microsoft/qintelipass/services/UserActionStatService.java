package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.ITrafficStatService;
import org.microsoft.qintelipass.util.ExpirationTimeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserActionStatService implements ITrafficStatService {
    @Autowired
    private IntegerRedisService redisService;
    private static final String PREFIX = "user:traffic:";
    private static final String ACTIVE_COUNT_PREFIX = "user:active:count";
    @Override
    public void recordTraffic(Long userId) {
        String key = PREFIX + userId;
        long trafficCount = redisService.increment(key, 1);
        redisService.expireAt(key, ExpirationTimeHelper.getNextDayTime());
        if (trafficCount == 65) {
            redisService.increment(ACTIVE_COUNT_PREFIX, 1);
            redisService.expireAt(ACTIVE_COUNT_PREFIX, ExpirationTimeHelper.getNextDayTime());
        }
    }

    @Override
    public void resetTraffic(Long userId) {
        String key = PREFIX + userId;
        redisService.setValue(key, 0);
    }

    @Override
    public List<Long> getAllActiveUsers() {
        return null;
    }

    @Override
    public Integer getActiveUsers() {
        return this.redisService.getValue(ACTIVE_COUNT_PREFIX);
    }
}
