package org.microsoft.qintelipass.services;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class RedisService {
    private static final DefaultRedisScript<Long> STORE_SMS_CODE = new DefaultRedisScript<>(
            "if redis.call('exists', KEYS[1]) == 1 then return 0 end " +
                    "local sent = tonumber(redis.call('get', KEYS[2]) or '0') " +
                    "if sent >= tonumber(ARGV[1]) then return -1 end " +
                    "redis.call('psetex', KEYS[3], ARGV[2], ARGV[3]) " +
                    "redis.call('psetex', KEYS[1], ARGV[4], '1') " +
                    "if sent == 0 then redis.call('psetex', KEYS[2], ARGV[5], '1') " +
                    "else redis.call('incr', KEYS[2]) end " +
                    "redis.call('del', KEYS[4]) " +
                    "return 1",
            Long.class);

    private static final DefaultRedisScript<Long> CONSUME_SMS_CODE = new DefaultRedisScript<>(
            "local actual = redis.call('get', KEYS[1]) " +
                    "if not actual then redis.call('del', KEYS[2]) return -1 end " +
                    "if actual == ARGV[1] then " +
                    "redis.call('del', KEYS[1]) redis.call('del', KEYS[2]) return 1 end " +
                    "local attempts = redis.call('incr', KEYS[2]) " +
                    "if attempts == 1 then " +
                    "local ttl = redis.call('pttl', KEYS[1]) " +
                    "if ttl > 0 then redis.call('pexpire', KEYS[2], ttl) end end " +
                    "if attempts >= tonumber(ARGV[2]) then " +
                    "redis.call('del', KEYS[1]) redis.call('del', KEYS[2]) return -2 end " +
                    "return 0",
            Long.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void setValue(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void setValue(String key, String value, Duration timeout) {
        redisTemplate.opsForValue().set(key, value, timeout);
    }

    public Object getValue(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public long storeSmsCodeIfAllowed(
            String cooldownKey,
            String hourlyCounterKey,
            String codeKey,
            String attemptsKey,
            String code,
            Duration codeTtl,
            Duration cooldown,
            int hourlyLimit,
            Duration hourlyWindow) {
        Long result = redisTemplate.execute(
                STORE_SMS_CODE,
                List.of(cooldownKey, hourlyCounterKey, codeKey, attemptsKey),
                String.valueOf(hourlyLimit),
                String.valueOf(codeTtl.toMillis()),
                code,
                String.valueOf(cooldown.toMillis()),
                String.valueOf(hourlyWindow.toMillis()));
        if (result == null) {
            throw new IllegalStateException("Redis did not return an SMS storage result");
        }
        return result;
    }

    public boolean consumeSmsCode(
            String codeKey,
            String attemptsKey,
            String expectedValue,
            int maximumAttempts) {
        Long result = redisTemplate.execute(
                CONSUME_SMS_CODE,
                List.of(codeKey, attemptsKey),
                expectedValue,
                String.valueOf(maximumAttempts));
        return Long.valueOf(1L).equals(result);
    }

    public void deleteValue(String key) {
        redisTemplate.delete(key);
    }
}
