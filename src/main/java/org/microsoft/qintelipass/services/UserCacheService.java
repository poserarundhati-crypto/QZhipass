package org.microsoft.qintelipass.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.dtos.UserDTO;
import org.microsoft.qintelipass.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserCacheService {
    private static final String USER_KEY_PREFIX = "user:";
    private static final String PHONE_INDEX_PREFIX = "user:phone:";
    private static final long CACHE_TTL_HOURS = 24;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    @Autowired
    public UserCacheService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void cacheUser(UserDTO user) {
        try {
            String userJson = objectMapper.writeValueAsString(user);
            String userKey = USER_KEY_PREFIX + user.getId();
            redisTemplate.opsForValue().set(userKey, userJson, CACHE_TTL_HOURS, TimeUnit.HOURS);

            if (user.getPhone() != null && !user.getPhone().isEmpty()) {
                String phoneKey = PHONE_INDEX_PREFIX + user.getPhone();
                redisTemplate.opsForValue().set(phoneKey, String.valueOf(user.getId()), CACHE_TTL_HOURS, TimeUnit.HOURS);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to cache user: {}", user.getId(), e);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, skipping user cache: {}", e.getMessage());
        }
    }

    public User getCachedUserById(Long userId) {
        if (userId == null) {
            return null;
        }
        String userKey = USER_KEY_PREFIX + userId;
        String userJson;
        try {
            userJson = redisTemplate.opsForValue().get(userKey);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, skipping cached user lookup: {}", e.getMessage());
            return null;
        }
        if (userJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(userJson, User.class);
        } catch (JsonProcessingException e) {
            log.warn("Invalid cached user data was evicted: userId={}", userId);
            deleteKeyQuietly(userKey);
            return null;
        }
    }

    public User getCachedUserByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }
        String phoneKey = PHONE_INDEX_PREFIX + phone;
        String userIdStr;
        try {
            userIdStr = redisTemplate.opsForValue().get(phoneKey);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, skipping cached phone lookup: {}", e.getMessage());
            return null;
        }
        if (userIdStr == null) {
            return null;
        }
        try {
            return getCachedUserById(Long.parseLong(userIdStr));
        } catch (NumberFormatException e) {
            log.error("Invalid user ID in cached phone index", e);
            try {
                redisTemplate.delete(phoneKey);
            } catch (RuntimeException redisException) {
                log.warn("Redis unavailable, could not delete invalid phone cache: {}", redisException.getMessage());
            }
            return null;
        }
    }

    public void deleteCachedUser(Long userId) {
        if (userId == null) {
            return;
        }
        User cachedUser = getCachedUserById(userId);
        try {
            if (cachedUser != null && cachedUser.getPhone() != null) {
                redisTemplate.delete(PHONE_INDEX_PREFIX + cachedUser.getPhone());
            }
            redisTemplate.delete(USER_KEY_PREFIX + userId);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, could not delete user cache: {}", e.getClass().getSimpleName());
        }
    }

    private void deleteKeyQuietly(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, could not delete invalid cache entry: {}",
                    e.getClass().getSimpleName());
        }
    }
}
