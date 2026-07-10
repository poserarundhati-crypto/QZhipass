package org.microsoft.qintelipass.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.repository.UserRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {
    @Mock private UserRepository userRepository;
    @Mock private UserCacheService userCacheService;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, userCacheService);
    }

    @Test
    void databaseStateIsAuthoritativeForAuthenticationSensitiveReads() {
        User deactivated = new User();
        deactivated.setId(42L);
        deactivated.setStatus(UserStatus.DEACTIVATED);
        when(userRepository.findById(42L)).thenReturn(Optional.of(deactivated));

        User result = service.getUserById(42L);

        assertSame(deactivated, result);
        assertEquals(UserStatus.DEACTIVATED, result.getStatus());
        verifyNoInteractions(userCacheService);
    }

    @Test
    void databaseFailureCannotFallBackToPotentiallyStaleNormalCache() {
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("database unavailable");
        when(userRepository.findById(42L)).thenThrow(failure);

        assertSame(failure, assertThrows(
                DataAccessResourceFailureException.class,
                () -> service.getUserById(42L)));

        verifyNoInteractions(userCacheService);
    }
}
