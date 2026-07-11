package org.microsoft.qintelipass.response;

public record UserAgentCallSettingsResponse(
        String hotkey,
        boolean mouseTriggerEnabled,
        String buttonLabel) {
}
