package org.microsoft.qintelipass.logins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.ISmsService;
import org.microsoft.qintelipass.services.UserService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MobileCodeLoginStrategyTest {
    @Mock private UserService userService;
    @Mock private ISmsService smsService;

    private MobileCodeLoginStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new MobileCodeLoginStrategy(userService, smsService);
    }

    @Test
    void invalidFormatDoesNotReadUserOrVerificationStorage() {
        when(smsService.isValidPhone("user:1")).thenReturn(false);

        ResponseBody<User> response = strategy.authenticate(Map.of(
                "phone", "user:1",
                "smsCode", "123456"));

        assertFalse(response.isSuccess());
        verify(smsService, never()).consumeSmsCode("user:1", "123456");
        verifyNoInteractions(userService);
    }

    @Test
    void incorrectCodeDoesNotRevealWhetherUserExists() {
        when(smsService.isValidPhone("13800000000")).thenReturn(true);
        when(smsService.consumeSmsCode("13800000000", "123456")).thenReturn(false);

        ResponseBody<User> response = strategy.authenticate(Map.of(
                "phone", "13800000000",
                "smsCode", "123456"));

        assertFalse(response.isSuccess());
        verifyNoInteractions(userService);
    }

    @Test
    void missingUserAfterValidCodeUsesSameGenericFailure() {
        when(smsService.isValidPhone("13800000000")).thenReturn(true);
        when(smsService.consumeSmsCode("13800000000", "123456")).thenReturn(true);
        when(userService.getUserByPhone("13800000000")).thenReturn(null);

        ResponseBody<User> response = strategy.authenticate(Map.of(
                "phone", "13800000000",
                "smsCode", "123456"));

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("Invalid phone number"));
    }

    @Test
    void validSingleUseCodeAuthenticatesNormalUser() {
        User user = new User();
        user.setStatus(UserStatus.NORMAL);
        when(smsService.isValidPhone("13800000000")).thenReturn(true);
        when(smsService.consumeSmsCode("13800000000", "123456")).thenReturn(true);
        when(userService.getUserByPhone("13800000000")).thenReturn(user);

        ResponseBody<User> response = strategy.authenticate(Map.of(
                "phone", "13800000000",
                "smsCode", "123456"));

        assertTrue(response.isSuccess());
        assertSame(user, response.getPayload());
        verify(userService).getUserByPhone("13800000000");
    }
}
