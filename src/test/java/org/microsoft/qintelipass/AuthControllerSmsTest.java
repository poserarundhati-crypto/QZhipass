package org.microsoft.qintelipass;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.controllers.AuthController;
import org.microsoft.qintelipass.exceptions.SmsRateLimitException;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.ConversationService;
import org.microsoft.qintelipass.services.ISmsService;
import org.microsoft.qintelipass.services.UserDetailsServiceImpl;
import org.microsoft.qintelipass.util.JwtUtil;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerSmsTest {
    @Mock private LoginStrategyFactory factory;
    @Mock private ISmsService smsService;
    @Mock private JwtUtil jwtUtil;
    @Mock private UserDetailsServiceImpl userDetailsService;
    @Mock private CredentialManager credentialManager;
    @Mock private IRegisterable registerService;
    @Mock private ConversationService conversationService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(
                factory,
                smsService,
                jwtUtil,
                userDetailsService,
                credentialManager,
                registerService,
                conversationService);
    }

    @Test
    void invalidPhoneCannotReachRedisStorage() {
        when(smsService.isValidPhone("user:1")).thenReturn(false);

        ResponseEntity<ResponseBody<Void>> response =
                controller.sendCode(Map.of("phone", "user:1"));

        assertEquals(400, response.getStatusCode().value());
        assertFalse(response.getBody().isSuccess());
        verify(smsService, never()).sendSmsCode("user:1");
    }

    @Test
    void validRequestReturnsSuccessWithoutCodePayload() {
        when(smsService.isValidPhone("13800000000")).thenReturn(true);

        ResponseEntity<ResponseBody<Void>> response =
                controller.sendCode(Map.of("phone", "13800000000"));

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isSuccess());
        assertEquals(null, response.getBody().getPayload());
        verify(smsService).sendSmsCode("13800000000");
    }

    @Test
    void rateLimitedRequestReturns429() {
        when(smsService.isValidPhone("13800000000")).thenReturn(true);
        doThrow(new SmsRateLimitException("retry later"))
                .when(smsService).sendSmsCode("13800000000");

        ResponseEntity<ResponseBody<Void>> response =
                controller.sendCode(Map.of("phone", "13800000000"));

        assertEquals(429, response.getStatusCode().value());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void redisFailureReturnsSafe503() {
        when(smsService.isValidPhone("13800000000")).thenReturn(true);
        doThrow(new DataAccessResourceFailureException("redis endpoint details"))
                .when(smsService).sendSmsCode("13800000000");

        ResponseEntity<ResponseBody<Void>> response =
                controller.sendCode(Map.of("phone", "13800000000"));

        assertEquals(503, response.getStatusCode().value());
        assertFalse(response.getBody().isSuccess());
        assertEquals("SMS verification service is temporarily unavailable.",
                response.getBody().getMessage());
    }
}
