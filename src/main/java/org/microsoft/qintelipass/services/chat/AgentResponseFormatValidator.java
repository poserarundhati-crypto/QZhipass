package org.microsoft.qintelipass.services.chat;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Component
public class AgentResponseFormatValidator {
    private static final Pattern NUMBERED_TEMPLATE_MARKER = Pattern.compile(
            "(?s).*1\\.\\s*.*2\\.\\s*.*3\\..*");
    private static final Pattern NUMBERED_RESPONSE = Pattern.compile(
            "(?s).*1\\.\\s+.+?2\\.\\s+.+?3\\.\\s+.+.*");

    public boolean requiresNumberedList(String agentPrompt) {
        return StringUtils.hasText(agentPrompt)
                && NUMBERED_TEMPLATE_MARKER.matcher(agentPrompt).matches();
    }

    public boolean isValid(String response, boolean numberedListRequired) {
        if (!StringUtils.hasText(response)) {
            return false;
        }
        return !numberedListRequired || NUMBERED_RESPONSE.matcher(response).matches();
    }
}
