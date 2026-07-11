package org.microsoft.qintelipass.request;

public record UpdateUserAgentCallSettingsRequest(
        String hotkey,
        Boolean mouseTriggerEnabled) {
}
