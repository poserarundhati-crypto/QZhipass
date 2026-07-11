package org.microsoft.qintelipass.controllers;

import org.microsoft.qintelipass.exceptions.UnauthorizedException;
import org.microsoft.qintelipass.request.UpdateUserAgentCallSettingsRequest;
import org.microsoft.qintelipass.response.ApiResponse;
import org.microsoft.qintelipass.response.UserAgentCallSettingsResponse;
import org.microsoft.qintelipass.security.SecurityUtil;
import org.microsoft.qintelipass.services.UserAgentCallSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/agent-call-settings")
public class UserAgentCallSettingsController {
    private final UserAgentCallSettingsService service;

    public UserAgentCallSettingsController(UserAgentCallSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<UserAgentCallSettingsResponse> getSettings() {
        return ApiResponse.ok(service.getSettings(currentUserId()));
    }

    @PutMapping
    public ApiResponse<UserAgentCallSettingsResponse> updateSettings(
            @RequestBody UpdateUserAgentCallSettingsRequest request) {
        return ApiResponse.ok(service.updateSettings(currentUserId(), request));
    }

    private Long currentUserId() {
        if (!SecurityUtil.isAuthenticated()) {
            throw new UnauthorizedException("Missing authenticated user.");
        }
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new UnauthorizedException("Missing authenticated user.");
        }
        return userId;
    }
}
