package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.entity.UserAgentCallSettings;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.exceptions.UnauthorizedException;
import org.microsoft.qintelipass.repository.UserAgentCallSettingsRepository;
import org.microsoft.qintelipass.request.UpdateUserAgentCallSettingsRequest;
import org.microsoft.qintelipass.response.UserAgentCallSettingsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAgentCallSettingsService {
    public static final String DEFAULT_HOTKEY = "!";
    public static final boolean DEFAULT_MOUSE_TRIGGER_ENABLED = true;
    public static final String BUTTON_LABEL = "调用agent";

    private static final int MAX_HOTKEY_LENGTH = 32;

    private final UserAgentCallSettingsRepository repository;

    public UserAgentCallSettingsService(UserAgentCallSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public UserAgentCallSettingsResponse getSettings(Long currentUserId) {
        requireCurrentUser(currentUserId);
        return repository.findById(currentUserId)
                .map(this::toResponse)
                .orElseGet(this::defaultResponse);
    }

    @Transactional
    public UserAgentCallSettingsResponse updateSettings(
            Long currentUserId,
            UpdateUserAgentCallSettingsRequest request) {
        requireCurrentUser(currentUserId);
        if (request == null) {
            throw new BadRequestException("Agent call settings are required.");
        }

        validateHotkey(request.hotkey());
        if (request.mouseTriggerEnabled() == null) {
            throw new BadRequestException("mouseTriggerEnabled is required.");
        }

        UserAgentCallSettings settings = repository.findById(currentUserId)
                .orElseGet(() -> newSettings(currentUserId));
        settings.setHotkey(request.hotkey());
        settings.setMouseTriggerEnabled(request.mouseTriggerEnabled());
        return toResponse(repository.save(settings));
    }

    private UserAgentCallSettings newSettings(Long currentUserId) {
        UserAgentCallSettings settings = new UserAgentCallSettings();
        settings.setUserId(currentUserId);
        return settings;
    }

    private void validateHotkey(String hotkey) {
        if (hotkey == null || hotkey.isEmpty() || hotkey.length() > MAX_HOTKEY_LENGTH) {
            throw new BadRequestException("hotkey must contain 1 to 32 visible ASCII characters.");
        }
        for (int index = 0; index < hotkey.length(); index++) {
            char character = hotkey.charAt(index);
            if (character < 0x21 || character > 0x7E) {
                throw new BadRequestException("hotkey must contain 1 to 32 visible ASCII characters.");
            }
        }
    }

    private void requireCurrentUser(Long currentUserId) {
        if (currentUserId == null) {
            throw new UnauthorizedException("Missing authenticated user.");
        }
    }

    private UserAgentCallSettingsResponse defaultResponse() {
        return new UserAgentCallSettingsResponse(
                DEFAULT_HOTKEY,
                DEFAULT_MOUSE_TRIGGER_ENABLED,
                BUTTON_LABEL);
    }

    private UserAgentCallSettingsResponse toResponse(UserAgentCallSettings settings) {
        return new UserAgentCallSettingsResponse(
                settings.getHotkey(),
                settings.isMouseTriggerEnabled(),
                BUTTON_LABEL);
    }
}
