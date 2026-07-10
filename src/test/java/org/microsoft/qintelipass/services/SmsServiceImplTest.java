package org.microsoft.qintelipass.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.exceptions.SmsRateLimitException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsServiceImplTest {
    @Mock
    private RedisService redisService;

    private SmsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SmsServiceImpl(redisService);
    }

    @Test
    void sendUsesNamespacedKeysAndServerSideLimitsWithoutExposingCode() {
        when(redisService.storeSmsCodeIfAllowed(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                any(Duration.class), any(Duration.class), anyInt(), any(Duration.class)))
                .thenReturn(1L);

        service.sendSmsCode("13800000000");

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(redisService).storeSmsCodeIfAllowed(
                eq("auth:sms:cooldown:{13800000000}"),
                eq("auth:sms:hourly:{13800000000}"),
                eq("auth:sms:code:{13800000000}"),
                eq("auth:sms:attempts:{13800000000}"),
                code.capture(),
                eq(Duration.ofMinutes(5)),
                eq(Duration.ofSeconds(60)),
                eq(5),
                eq(Duration.ofHours(1)));
        assertTrue(code.getValue().matches("\\d{6}"));
    }

    @Test
    void arbitraryRedisKeyInputIsRejectedAsInvalidPhone() {
        assertThrows(IllegalArgumentException.class,
                () -> service.sendSmsCode("user:9007199254740993"));

        verifyNoInteractions(redisService);
    }

    @Test
    void cooldownAndHourlyLimitReturnStableRateLimitFailure() {
        when(redisService.storeSmsCodeIfAllowed(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                any(Duration.class), any(Duration.class), anyInt(), any(Duration.class)))
                .thenReturn(0L, -1L);

        assertThrows(SmsRateLimitException.class,
                () -> service.sendSmsCode("13800000000"));
        assertThrows(SmsRateLimitException.class,
                () -> service.sendSmsCode("13800000000"));
    }

    @Test
    void verificationUsesNamespacedAttemptLimitedAtomicConsume() {
        when(redisService.consumeSmsCode(
                "auth:sms:code:{13800000000}",
                "auth:sms:attempts:{13800000000}",
                "123456",
                5)).thenReturn(true);

        assertTrue(service.consumeSmsCode("13800000000", "123456"));
        assertFalse(service.consumeSmsCode("13800000000", "not-code"));

        verify(redisService).consumeSmsCode(
                "auth:sms:code:{13800000000}",
                "auth:sms:attempts:{13800000000}",
                "123456",
                5);
        verify(redisService, never()).consumeSmsCode(
                anyString(), anyString(), eq("not-code"), anyInt());
    }
}
