package org.microsoft.qintelipass;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.repository.UserRepository;
import org.microsoft.qintelipass.security.AuthenticatedUser;
import org.microsoft.qintelipass.services.UserCacheService;
import org.microsoft.qintelipass.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:admin-authorization;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.data.redis.timeout=100ms",
                "app.security.admin-user-ids=9007199254740993"
        })
class AdminAuthorizationIntegrationTest {
    private static final long ADMIN_ID = 9_007_199_254_740_993L;
    private static final long USER_ID = ADMIN_ID + 1;

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserCacheService userCacheService;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private User admin;
    private User ordinaryUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        admin = userRepository.saveAndFlush(user(ADMIN_ID, "admin-user", "13800000011"));
        ordinaryUser = userRepository.saveAndFlush(user(USER_ID, "ordinary-user", "13800000012"));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void adminEndpointsRequireServerConfiguredAdminAuthority() throws Exception {
        HttpResponse<String> unauthenticated = request("GET", "/api/v1/admin/users", null);
        assertEquals(401, unauthenticated.statusCode());
        assertFalse(json(unauthenticated).path("success").asBoolean());

        String ordinaryToken = tokenFor(ordinaryUser);
        HttpResponse<String> forbiddenList = request("GET", "/api/v1/admin/users", ordinaryToken);
        assertEquals(403, forbiddenList.statusCode());
        assertEquals("无权访问该资源", json(forbiddenList).path("message").asText());

        HttpResponse<String> forbiddenDelete = request(
                "DELETE", "/api/v1/admin/users/" + ADMIN_ID, ordinaryToken);
        assertEquals(403, forbiddenDelete.statusCode());
        assertEquals(UserStatus.NORMAL, userRepository.findById(ADMIN_ID).orElseThrow().getStatus());

        HttpResponse<String> adminList = request("GET", "/api/v1/admin/users", tokenFor(admin));
        assertEquals(200, adminList.statusCode());
        JsonNode adminItems = json(adminList).path("items");
        assertTrue(adminItems.isArray());
        assertTrue(adminItems.valueStream()
                .map(item -> item.path("id"))
                .anyMatch(id -> id.isTextual() && id.asText().equals(Long.toString(ADMIN_ID))),
                adminList.body());
        assertFalse(adminList.body().contains("not-a-real-password-hash"));

        HttpResponse<String> adminDelete = request(
                "DELETE", "/api/v1/admin/users/" + USER_ID, tokenFor(admin));
        assertEquals(200, adminDelete.statusCode());
        assertEquals(UserStatus.DEACTIVATED, userRepository.findById(USER_ID).orElseThrow().getStatus());
    }

    private User user(long id, String username, String phone) {
        User user = new User();
        user.setId(id);
        user.setName(username);
        user.setPhone(phone);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("not-a-real-password-hash");
        user.setStatus(UserStatus.NORMAL);
        return user;
    }

    private String tokenFor(User user) {
        AuthenticatedUser principal = AuthenticatedUser.builder()
                .userId(user.getId())
                .username(user.getName())
                .password(user.getPasswordHash())
                .build();
        return jwtUtil.generateToken(principal, user.getId());
    }

    private HttpResponse<String> request(String method, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(
                builder.method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }
}
