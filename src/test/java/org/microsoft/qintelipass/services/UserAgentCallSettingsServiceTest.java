package org.microsoft.qintelipass.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.entity.UserAgentCallSettings;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.exceptions.UnauthorizedException;
import org.microsoft.qintelipass.repository.UserAgentCallSettingsRepository;
import org.microsoft.qintelipass.request.UpdateUserAgentCallSettingsRequest;
import org.microsoft.qintelipass.response.UserAgentCallSettingsResponse;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAgentCallSettingsServiceTest {
    private static final Long CURRENT_USER_ID = 1001L;

    @Mock
    private UserAgentCallSettingsRepository repository;

    private UserAgentCallSettingsService service;

    @BeforeEach
    void setUp() {
        service = new UserAgentCallSettingsService(repository);
    }

    @Test
    void returnsDefaultsWithoutCreatingARowWhenSettingsDoNotExist() {
        when(repository.findById(CURRENT_USER_ID)).thenReturn(Optional.empty());

        UserAgentCallSettingsResponse response = service.getSettings(CURRENT_USER_ID);

        assertAll(
                () -> assertEquals("!", response.hotkey()),
                () -> assertEquals(true, response.mouseTriggerEnabled()),
                () -> assertEquals("调用agent", response.buttonLabel()));
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createsSettingsForTheCurrentUserOnFirstUpdate() {
        when(repository.findById(CURRENT_USER_ID)).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(UserAgentCallSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserAgentCallSettingsResponse response = service.updateSettings(
                CURRENT_USER_ID,
                new UpdateUserAgentCallSettingsRequest("Alt+J", false));

        ArgumentCaptor<UserAgentCallSettings> captor = ArgumentCaptor.forClass(UserAgentCallSettings.class);
        verify(repository).save(captor.capture());
        assertAll(
                () -> assertEquals(CURRENT_USER_ID, captor.getValue().getUserId()),
                () -> assertEquals("Alt+J", captor.getValue().getHotkey()),
                () -> assertEquals(false, captor.getValue().isMouseTriggerEnabled()),
                () -> assertEquals("Alt+J", response.hotkey()),
                () -> assertEquals(false, response.mouseTriggerEnabled()),
                () -> assertEquals("调用agent", response.buttonLabel()));
    }

    @Test
    void updatesTheExistingCurrentUserRow() {
        UserAgentCallSettings existing = settings(CURRENT_USER_ID, "!", true);
        when(repository.findById(CURRENT_USER_ID)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        UserAgentCallSettingsResponse response = service.updateSettings(
                CURRENT_USER_ID,
                new UpdateUserAgentCallSettingsRequest("Ctrl+1", true));

        assertAll(
                () -> assertEquals("Ctrl+1", existing.getHotkey()),
                () -> assertEquals(true, existing.isMouseTriggerEnabled()),
                () -> assertEquals("调用agent", response.buttonLabel()));
        verify(repository).save(existing);
    }

    @Test
    void rejectsMissingCurrentUserBeforeRepositoryAccess() {
        assertThrows(UnauthorizedException.class, () -> service.getSettings(null));
        assertThrows(UnauthorizedException.class, () -> service.updateSettings(
                null,
                new UpdateUserAgentCallSettingsRequest("!", true)));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsNullEmptyTooLongAndNonVisibleAsciiHotkeys() {
        String tooLong = "A".repeat(33);

        assertAll(
                () -> assertInvalidHotkey(null),
                () -> assertInvalidHotkey(""),
                () -> assertInvalidHotkey(tooLong),
                () -> assertInvalidHotkey("Alt J"),
                () -> assertInvalidHotkey("Alt\nJ"),
                () -> assertInvalidHotkey("快捷键"));
        verifyNoInteractions(repository);
    }

    @Test
    void acceptsAllVisibleAsciiBoundaryCharactersAndMaximumLength() {
        String hotkey = "!" + "A".repeat(30) + "~";
        when(repository.findById(CURRENT_USER_ID)).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(UserAgentCallSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserAgentCallSettingsResponse response = service.updateSettings(
                CURRENT_USER_ID,
                new UpdateUserAgentCallSettingsRequest(hotkey, true));

        assertEquals(hotkey, response.hotkey());
    }

    @Test
    void rejectsNullRequestAndNullMouseTriggerFlag() {
        assertAll(
                () -> assertThrows(BadRequestException.class,
                        () -> service.updateSettings(CURRENT_USER_ID, null)),
                () -> assertThrows(BadRequestException.class,
                        () -> service.updateSettings(
                                CURRENT_USER_ID,
                                new UpdateUserAgentCallSettingsRequest("!", null))));
        verifyNoInteractions(repository);
    }

    private void assertInvalidHotkey(String hotkey) {
        assertThrows(BadRequestException.class, () -> service.updateSettings(
                CURRENT_USER_ID,
                new UpdateUserAgentCallSettingsRequest(hotkey, true)));
    }

    private UserAgentCallSettings settings(Long userId, String hotkey, boolean mouseTriggerEnabled) {
        UserAgentCallSettings settings = new UserAgentCallSettings();
        settings.setUserId(userId);
        settings.setHotkey(hotkey);
        settings.setMouseTriggerEnabled(mouseTriggerEnabled);
        return settings;
    }
}
