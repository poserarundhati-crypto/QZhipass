package org.microsoft.qintelipass.services;

import lombok.NonNull;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class IntegerRedisService implements IRedisService<Integer>{
    private final RedisTemplate<String, String> redisTemplate;

    public IntegerRedisService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void setValue(String key, Integer value) {
        redisTemplate.opsForValue().set(key, Integer.toString(value));
    }

    @Override
    public @NonNull Integer getValue(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Redis integer value has an invalid format", exception);
        }
    }

    public long increment(String key, long delta) {
        Long value = redisTemplate.opsForValue().increment(key, delta);
        return value == null ? 0L : value;
    }

    public void expireAt(String key, Instant expiration) {
        redisTemplate.expireAt(key, expiration);
    }

    @Override
    public void deleteValue(String key) {
        redisTemplate.delete(key);
    }
}
