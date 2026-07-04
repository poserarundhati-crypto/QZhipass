package org.microsoft.qintelipass.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class StringRedisService implements IRedisService<String>{
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public void setValue(String key, String value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, value was not persisted for key {}: {}", key, e.getMessage());
        }
    }

    @Override
    public String getValue(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, returning null for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public void deleteValue(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, could not delete key {}: {}", key, e.getMessage());
        }
    }
}
