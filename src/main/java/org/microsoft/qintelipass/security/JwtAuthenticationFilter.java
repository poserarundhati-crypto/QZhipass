package org.microsoft.qintelipass.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.microsoft.qintelipass.ITrafficStatService;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.UserService;
import org.microsoft.qintelipass.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final ITrafficStatService trafficStatService;
    private final AdminAccessPolicy adminAccessPolicy;
    private final ObjectMapper objectMapper;

    @Autowired
    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   UserService userService,
                                   ITrafficStatService trafficStatService,
                                   AdminAccessPolicy adminAccessPolicy,
                                   ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.trafficStatService = trafficStatService;
        this.adminAccessPolicy = adminAccessPolicy;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @Nullable HttpServletResponse response, @Nullable FilterChain filterChain) throws ServletException, IOException {
        final String authorizationHeader = request.getHeader("Authorization");

        String jwt;

        if (authorizationHeader != null && authorizationHeader.startsWith(JwtUtil.BEARER_PREFIX)) {
            jwt = authorizationHeader.substring(JwtUtil.BEARER_PREFIX.length());

            try {
                if (jwtUtil.validateToken(jwt)) {
                    Long userId = null;
                    try {
                        userId = jwtUtil.extractUserId(jwt);
                    } catch (Exception e) {
                        log.warn("Could not extract user ID from token, will try to find by username");
                    }

                    String username = jwtUtil.extractUsername(jwt);
                    User user = null;

                    if (userId != null) {
                        user = userService.getUserById(userId);
                    } else {
                        user = userService.findByUsername(username);
                    }

                    if (user != null && UserStatus.NORMAL.equals(user.getStatus())) {
//                        trafficStatService.recordTraffic(user.getId());
                        AuthenticatedUser authenticatedUser = AuthenticatedUser.builder()
                                .userId(user.getId())
                                .username(user.getName())
                                .password(user.getPasswordHash())
                                .admin(adminAccessPolicy.isAdmin(user.getId()))
                                .build();

                        UsernamePasswordAuthenticationToken authenticationToken =
                                new UsernamePasswordAuthenticationToken(authenticatedUser, null, authenticatedUser.getAuthorities());
                        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                        log.debug("User authenticated: userId={}", user.getId());
                    } else if (user != null) {
                        log.info("Authentication rejected because account is not active. userId={}", user.getId());
                    }
                }
            } catch (DataAccessException e) {
                log.error("Authentication user lookup is unavailable: {}", e.getClass().getSimpleName());
                writeServiceUnavailable(response);
                return;
            } catch (Exception e) {
                log.info("Token validation failed: {}", e.getClass().getSimpleName());
            }
        }

        if (filterChain != null) {
            filterChain.doFilter(request, response);
        }
    }

    private void writeServiceUnavailable(HttpServletResponse response) throws IOException, ServletException {
        if (response == null) {
            throw new ServletException("Authentication dependency is unavailable");
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ResponseBody.<Void>builder()
                .success(false)
                .message("认证服务暂时不可用")
                .payload(null)
                .build());
    }
}
