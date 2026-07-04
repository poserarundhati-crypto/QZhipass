package org.microsoft.qintelipass.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.dtos.UserDTO;
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

    @Autowired
    private ObjectMapper objectMapper;

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

    public UserDTO getCachedUserById(Long userId) {
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
            return objectMapper.readValue(userJson, UserDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cached user: {}", userId, e);
            deleteCachedUser(userId);
            return null;
        }
    }

    public UserDTO getCachedUserByPhone(String phone) {
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
            log.error("Invalid user ID in cache for phone: {}", phone, e);
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
        UserDTO cachedUser = getCachedUserById(userId);
        try {
            if (cachedUser != null && cachedUser.getPhone() != null) {
                redisTemplate.delete(PHONE_INDEX_PREFIX + cachedUser.getPhone());
            }
            redisTemplate.delete(USER_KEY_PREFIX + userId);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, could not delete user cache: {}", e.getMessage());
        }
    }
}
