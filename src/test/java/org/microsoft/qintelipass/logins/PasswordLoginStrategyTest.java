package org.microsoft.qintelipass.logins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordLoginStrategyTest {
    private static final String LEGACY_PASSWORD = "legacy-password";

    @Mock
    private UserService userService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setName("legacy-user");
        user.setPhone("13800000000");
        user.setEmail("legacy@example.com");
        user.setPasswordHash("stored-hash");
        user.setStatus(UserStatus.NORMAL);
        when(passwordEncoder.matches(LEGACY_PASSWORD, "stored-hash")).thenReturn(true);
    }

    @Test
    void mobileLoginAcceptsExistingPasswordThatPredatesCurrentRegistrationPolicy() {
        when(userService.getUserByPhone("13800000000")).thenReturn(user);
        MobilePasswordStrategy strategy = new MobilePasswordStrategy(userService, passwordEncoder);

        assertTrue(strategy.authenticate(Map.of(
                "phone", "13800000000",
                "password", LEGACY_PASSWORD)).isSuccess());
    }

    @Test
    void emailLoginAcceptsExistingPasswordThatPredatesCurrentRegistrationPolicy() {
        when(userService.getUserByEmail("legacy@example.com")).thenReturn(user);
        EmailPasswordStrategy strategy = new EmailPasswordStrategy(userService, passwordEncoder);

        assertTrue(strategy.authenticate(Map.of(
                "email", "legacy@example.com",
                "password", LEGACY_PASSWORD)).isSuccess());
    }
}
