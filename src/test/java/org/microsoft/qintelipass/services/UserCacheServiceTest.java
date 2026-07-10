package org.microsoft.qintelipass.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCacheServiceTest {
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private UserCacheService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new UserCacheService(new ObjectMapper());
        org.springframework.test.util.ReflectionTestUtils.setField(
                service, "redisTemplate", redisTemplate);
    }

    @Test
    void invalidCachedJsonIsDeletedWithoutRecursiveDeserialization() {
        when(valueOperations.get("user:42")).thenReturn("{invalid-json");

        assertNull(service.getCachedUserById(42L));

        verify(valueOperations).get("user:42");
        verify(redisTemplate).delete("user:42");
    }
}
