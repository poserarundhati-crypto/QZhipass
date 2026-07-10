package org.microsoft.qintelipass.services;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.microsoft.qintelipass.exceptions.UnauthorizedException;
import org.microsoft.qintelipass.security.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
// Uses the JWT-populated SecurityContext; the Redis token lookup remains as a legacy fallback.
public class CurrentUserService {
    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final AuthTokenService authTokenService;

    public CurrentUserService(AuthTokenService authTokenService) {
        this.authTokenService = authTokenService;
    }

    // All conversation APIs use this server-authenticated id as their trusted current user identity.
    public Long requireUserId(HttpServletRequest request) {
        Long authenticatedUserId = SecurityUtil.getCurrentUserId();
        if (authenticatedUserId != null) {
            return authenticatedUserId;
        }

        String token = resolveToken(request)
                .orElseThrow(() -> new UnauthorizedException("Missing access token."));
        return authTokenService.resolveUserId(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired access token."));
    }

    // Supports Authorization Bearer, X-Access-Token, and same-site access_token cookie.
    private Optional<String> resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return Optional.of(authorization.substring(7).trim());
        }

        String tokenHeader = request.getHeader("X-Access-Token");
        if (StringUtils.hasText(tokenHeader)) {
            return Optional.of(tokenHeader.trim());
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (ACCESS_TOKEN_COOKIE.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return Optional.of(cookie.getValue().trim());
                }
            }
        }

        return Optional.empty();
    }
}
