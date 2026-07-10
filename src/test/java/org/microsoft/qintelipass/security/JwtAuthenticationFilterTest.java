package org.microsoft.qintelipass.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.microsoft.qintelipass.ITrafficStatService;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.services.UserService;
import org.microsoft.qintelipass.util.JwtUtil;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserService userService;
    @Mock
    private ITrafficStatService trafficStatService;
    @Mock
    private AdminAccessPolicy adminAccessPolicy;
    @Mock
    private FilterChain filterChain;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(
                jwtUtil,
                userService,
                trafficStatService,
                adminAccessPolicy,
                objectMapper);
    }

    @Test
    void tokenWithMissingUserIdCannotRebindToARecreatedUsername() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer test-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtUtil.validateToken("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(42L);
        when(jwtUtil.extractUsername("test-token")).thenReturn("reused-name");
        when(userService.getUserById(42L)).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userService, never()).findByUsername("reused-name");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void databaseDeactivatedUserCannotAuthenticateWithExistingToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer test-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        User user = new User();
        user.setId(42L);
        user.setName("deactivated-user");
        user.setStatus(UserStatus.DEACTIVATED);
        when(jwtUtil.validateToken("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(42L);
        when(jwtUtil.extractUsername("test-token")).thenReturn("deactivated-user");
        when(userService.getUserById(42L)).thenReturn(user);

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void legacyTokenWithoutUserIdStillUsesDatabaseUsernameLookup() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer legacy-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        User user = new User();
        user.setId(43L);
        user.setName("legacy-user");
        user.setPasswordHash("unused");
        user.setStatus(UserStatus.NORMAL);
        when(jwtUtil.validateToken("legacy-token")).thenReturn(true);
        when(jwtUtil.extractUserId("legacy-token"))
                .thenThrow(new IllegalArgumentException("missing user id claim"));
        when(jwtUtil.extractUsername("legacy-token")).thenReturn("legacy-user");
        when(userService.findByUsername("legacy-user")).thenReturn(user);

        filter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userService).findByUsername("legacy-user");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void databaseFailureDuringAuthenticationReturnsSafeJson503() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer test-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtUtil.validateToken("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(42L);
        when(jwtUtil.extractUsername("test-token")).thenReturn("user");
        when(userService.getUserById(42L))
                .thenThrow(new DataAccessResourceFailureException("database offline"));

        filter.doFilter(request, response, filterChain);

        assertEquals(503, response.getStatus());
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertFalse(body.path("success").asBoolean());
        assertEquals("认证服务暂时不可用", body.path("message").asText());
        verifyNoInteractions(filterChain);
    }
}
