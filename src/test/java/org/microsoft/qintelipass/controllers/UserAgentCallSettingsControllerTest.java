package org.microsoft.qintelipass.controllers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.exceptions.UnauthorizedException;
import org.microsoft.qintelipass.request.UpdateUserAgentCallSettingsRequest;
import org.microsoft.qintelipass.response.ApiResponse;
import org.microsoft.qintelipass.response.UserAgentCallSettingsResponse;
import org.microsoft.qintelipass.security.AuthenticatedUser;
import org.microsoft.qintelipass.services.UserAgentCallSettingsService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAgentCallSettingsControllerTest {
    private static final Long CURRENT_USER_ID = 2001L;

    @Mock
    private UserAgentCallSettingsService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getUsesOnlyTheAuthenticatedUserId() {
        authenticate(CURRENT_USER_ID);
        UserAgentCallSettingsResponse settings = new UserAgentCallSettingsResponse("!", true, "调用agent");
        when(service.getSettings(CURRENT_USER_ID)).thenReturn(settings);
        UserAgentCallSettingsController controller = new UserAgentCallSettingsController(service);

        ApiResponse<UserAgentCallSettingsResponse> response = controller.getSettings();

        assertEquals(settings, response.data());
        verify(service).getSettings(CURRENT_USER_ID);
    }

    @Test
    void updateUsesOnlyTheAuthenticatedUserId() {
        authenticate(CURRENT_USER_ID);
        UpdateUserAgentCallSettingsRequest request =
                new UpdateUserAgentCallSettingsRequest("Alt+J", false);
        UserAgentCallSettingsResponse settings =
                new UserAgentCallSettingsResponse("Alt+J", false, "调用agent");
        when(service.updateSettings(CURRENT_USER_ID, request)).thenReturn(settings);
        UserAgentCallSettingsController controller = new UserAgentCallSettingsController(service);

        ApiResponse<UserAgentCallSettingsResponse> response = controller.updateSettings(request);

        assertEquals(settings, response.data());
        verify(service).updateSettings(CURRENT_USER_ID, request);
    }

    @Test
    void rejectsUnauthenticatedRequestsBeforeCallingTheService() {
        UserAgentCallSettingsController controller = new UserAgentCallSettingsController(service);

        assertThrows(UnauthorizedException.class, controller::getSettings);
        verifyNoInteractions(service);
    }

    private void authenticate(Long userId) {
        AuthenticatedUser principal = AuthenticatedUser.builder()
                .userId(userId)
                .username("settings-user")
                .password("unused")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()));
    }
}
