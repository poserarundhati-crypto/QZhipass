package org.microsoft.qintelipass.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AdminAccessPolicy {
    private final Set<Long> adminUserIds;

    public AdminAccessPolicy(@Value("${app.security.admin-user-ids:}") String configuredUserIds) {
        this.adminUserIds = Arrays.stream(configuredUserIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(this::parseUserId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(Long userId) {
        return userId != null && adminUserIds.contains(userId);
    }

    private Long parseUserId(String value) {
        try {
            long userId = Long.parseLong(value);
            if (userId <= 0) {
                throw new IllegalArgumentException("ADMIN_USER_IDS must contain positive numeric user IDs");
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("ADMIN_USER_IDS must contain comma-separated numeric user IDs", exception);
        }
    }
}
