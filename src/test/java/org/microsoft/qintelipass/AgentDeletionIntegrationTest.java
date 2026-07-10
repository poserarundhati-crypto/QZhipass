package org.microsoft.qintelipass;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.dtos.UserTokenUsageDTO;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.Agent;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.repository.AgentRepository;
import org.microsoft.qintelipass.repository.UserRepository;
import org.microsoft.qintelipass.security.AuthenticatedUser;
import org.microsoft.qintelipass.services.TokenUsageService;
import org.microsoft.qintelipass.services.UserCacheService;
import org.microsoft.qintelipass.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:agent-deletion;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.data.redis.timeout=100ms"
        })
class AgentDeletionIntegrationTest {
    private static final long AGENT_ID = 9_007_199_254_740_993L;

    @LocalServerPort
    private int port;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserCacheService userCacheService;

    @MockitoBean
    private TokenUsageService tokenUsageService;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private User owner;
    private User otherUser;
    private String ownerToken;
    private String otherUserToken;

    @BeforeEach
    void setUp() {
        agentRepository.deleteAll();
        userRepository.deleteAll();

        owner = userRepository.saveAndFlush(user("agent-owner", "13800000001"));
        otherUser = userRepository.saveAndFlush(user("other-owner", "13800000002"));
        ownerToken = tokenFor(owner);
        otherUserToken = tokenFor(otherUser);
        when(tokenUsageService.checkTokenLimit(anyLong())).thenReturn(true);
        when(tokenUsageService.getUserTokenUsage(anyLong())).thenReturn(UserTokenUsageDTO.builder()
                .userId(owner.getId())
                .userName(owner.getName())
                .tokenUsed(10003L)
                .tokenLimit(100000L)
                .build());
    }

    @AfterEach
    void tearDown() {
        agentRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void confirmationListSearchAndDetailReturnOnlyActiveOwnedAgent() throws Exception {
        saveAgent(AGENT_ID, "合同审查助手", owner.getId(), Agent.STATUS_ACTIVE);
        saveAgent(AGENT_ID + 1, "已删除助手", owner.getId(), Agent.STATUS_DELETED);
        saveAgent(AGENT_ID + 2, "他人的助手", otherUser.getId(), Agent.STATUS_ACTIVE);

        HttpResponse<String> listResponse = request("GET", "/api/v1/agent", ownerToken, null);
        assertEquals(200, listResponse.statusCode());
        JsonNode list = json(listResponse);
        assertEquals(1, list.path("payload").path("total").asInt());
        assertEquals(Long.toString(AGENT_ID), list.path("payload").path("items").get(0).path("agentId").asText());

        String keyword = URLEncoder.encode("合同", StandardCharsets.UTF_8);
        HttpResponse<String> searchResponse = request("GET", "/api/v1/agent?q=" + keyword, ownerToken, null);
        assertEquals(200, searchResponse.statusCode());
        assertEquals(1, json(searchResponse).path("payload").path("total").asInt());

        HttpResponse<String> detailResponse = request("GET", "/api/v1/agent/" + AGENT_ID, ownerToken, null);
        assertEquals(200, detailResponse.statusCode());
        assertEquals("合同审查助手", json(detailResponse).path("payload").path("agentName").asText());

        HttpResponse<String> confirmationResponse = request(
                "GET", "/api/v1/agent/" + AGENT_ID + "/delete-confirmation", ownerToken, null);
        assertEquals(200, confirmationResponse.statusCode());
        JsonNode confirmation = json(confirmationResponse).path("payload");
        assertEquals(Long.toString(AGENT_ID), confirmation.path("agentId").asText());
        assertEquals("合同审查助手", confirmation.path("agentName").asText());
        assertTrue(confirmation.path("deletable").asBoolean());
        assertEquals("确定要删除“合同审查助手”吗？", confirmation.path("confirmationMessage").asText());

        HttpResponse<String> callResponse = request(
                "POST", "/api/v1/agent/" + AGENT_ID + "/call", ownerToken, null);
        assertEquals(200, callResponse.statusCode());
        assertTrue(json(callResponse).path("success").asBoolean());
    }

    @Test
    void deleteIsIdempotentAndDeletedAgentCannotBeReadSearchedUpdatedOrCalled() throws Exception {
        saveAgent(AGENT_ID, "合同审查助手", owner.getId(), Agent.STATUS_ACTIVE);

        HttpResponse<String> firstDelete = request("DELETE", "/api/v1/agent/" + AGENT_ID, ownerToken, null);
        assertEquals(200, firstDelete.statusCode());
        JsonNode firstPayload = json(firstDelete).path("payload");
        assertTrue(firstPayload.path("deleted").asBoolean());
        assertFalse(firstPayload.path("alreadyDeleted").asBoolean());
        assertEquals(Agent.STATUS_DELETED, agentRepository.findById(AGENT_ID).orElseThrow().getStatus());

        HttpResponse<String> listAfterDelete = request("GET", "/api/v1/agent", ownerToken, null);
        assertEquals(200, listAfterDelete.statusCode());
        assertEquals(0, json(listAfterDelete).path("payload").path("total").asInt());
        assertTrue(json(listAfterDelete).path("payload").path("items").isEmpty());

        HttpResponse<String> searchAfterDelete = request(
                "GET", "/api/v1/agent?q=%E5%90%88%E5%90%8C", ownerToken, null);
        assertEquals(200, searchAfterDelete.statusCode());
        assertEquals(0, json(searchAfterDelete).path("payload").path("total").asInt());
        assertTrue(json(searchAfterDelete).path("payload").path("items").isEmpty());
        assertEquals(404, request("GET", "/api/v1/agent/" + AGENT_ID, ownerToken, null).statusCode());
        assertEquals(404, request(
                "GET", "/api/v1/agent/" + AGENT_ID + "/delete-confirmation", ownerToken, null).statusCode());
        assertEquals(404, request(
                "PUT", "/api/v1/agent/" + AGENT_ID, ownerToken, "{\"agentName\":\"新名称\"}").statusCode());
        assertEquals(404, request(
                "POST", "/api/v1/agent/" + AGENT_ID + "/call", ownerToken, null).statusCode());
        assertEquals(200, request("POST", "/api/v1/agent/call", ownerToken, null).statusCode());

        Agent persisted = agentRepository.findById(AGENT_ID).orElseThrow();
        assertEquals("合同审查助手", persisted.getAgentName());
        assertEquals(Agent.STATUS_DELETED, persisted.getStatus());

        HttpResponse<String> secondDelete = request("DELETE", "/api/v1/agent/" + AGENT_ID, ownerToken, null);
        assertEquals(200, secondDelete.statusCode());
        JsonNode secondPayload = json(secondDelete).path("payload");
        assertTrue(secondPayload.path("deleted").asBoolean());
        assertTrue(secondPayload.path("alreadyDeleted").asBoolean());
        assertFalse(secondDelete.body().toLowerCase().contains("api_key"));
        assertFalse(secondDelete.body().toLowerCase().contains("apikey"));
    }

    @Test
    void concurrentDeleteRequestsAllSucceedAndOnlyOneChangesState() throws Exception {
        saveAgent(AGENT_ID, "并发删除助手", owner.getId(), Agent.STATUS_ACTIVE);
        int workerCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < workerCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("并发删除启动超时");
                    }
                    return request("DELETE", "/api/v1/agent/" + AGENT_ID, ownerToken, null);
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int firstDeleteCount = 0;
            int alreadyDeletedCount = 0;
            for (Future<HttpResponse<String>> future : futures) {
                HttpResponse<String> response = future.get(15, TimeUnit.SECONDS);
                assertEquals(200, response.statusCode());
                JsonNode payload = json(response).path("payload");
                assertTrue(payload.path("deleted").asBoolean());
                if (payload.path("alreadyDeleted").asBoolean()) {
                    alreadyDeletedCount++;
                } else {
                    firstDeleteCount++;
                }
            }

            assertEquals(1, firstDeleteCount);
            assertEquals(workerCount - 1, alreadyDeletedCount);
            assertEquals(Agent.STATUS_DELETED, agentRepository.findById(AGENT_ID).orElseThrow().getStatus());
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void anotherUserCannotConfirmOrDeleteAgent() throws Exception {
        saveAgent(AGENT_ID, "合同审查助手", owner.getId(), Agent.STATUS_ACTIVE);

        assertEquals(404, request(
                "GET", "/api/v1/agent/" + AGENT_ID + "/delete-confirmation", otherUserToken, null).statusCode());
        HttpResponse<String> response = request("DELETE", "/api/v1/agent/" + AGENT_ID, otherUserToken, null);
        assertEquals(404, response.statusCode());
        assertEquals("Agent不存在或无权操作", json(response).path("message").asText());
        assertEquals(Agent.STATUS_ACTIVE, agentRepository.findById(AGENT_ID).orElseThrow().getStatus());
        assertFalse(response.body().contains("合同审查助手"));
    }

    @Test
    void unauthenticatedDeleteReturnsJson401() throws Exception {
        saveAgent(AGENT_ID, "合同审查助手", owner.getId(), Agent.STATUS_ACTIVE);

        HttpResponse<String> response = request("DELETE", "/api/v1/agent/" + AGENT_ID, null, null);

        assertEquals(401, response.statusCode());
        JsonNode body = json(response);
        assertFalse(body.path("success").asBoolean());
        assertEquals("未登录或登录已失效", body.path("message").asText());
    }

    @Test
    void invalidAndMissingAgentIdsReturnStableErrors() throws Exception {
        HttpResponse<String> invalid = request("DELETE", "/api/v1/agent/not-a-number", ownerToken, null);
        assertEquals(400, invalid.statusCode());
        assertEquals("请求参数格式无效", json(invalid).path("message").asText());

        HttpResponse<String> invalidCall = request(
                "POST", "/api/v1/agent/not-a-number/call", ownerToken, null);
        assertEquals(400, invalidCall.statusCode());

        HttpResponse<String> nonPositive = request("DELETE", "/api/v1/agent/0", ownerToken, null);
        assertEquals(400, nonPositive.statusCode());
        assertEquals("agentId格式无效", json(nonPositive).path("message").asText());

        HttpResponse<String> missing = request("DELETE", "/api/v1/agent/999999", ownerToken, null);
        assertEquals(404, missing.statusCode());
        assertEquals("Agent不存在或无权操作", json(missing).path("message").asText());
    }

    private User user(String username, String phone) {
        User user = new User();
        user.setName(username);
        user.setPhone(phone);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("not-a-real-password-hash");
        user.setStatus(UserStatus.NORMAL);
        return user;
    }

    private void saveAgent(long id, String name, long createdBy, int status) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setAgentName(name);
        agent.setCreatedBy(createdBy);
        agent.setStatus(status);
        agentRepository.saveAndFlush(agent);
    }

    private String tokenFor(User user) {
        AuthenticatedUser principal = AuthenticatedUser.builder()
                .userId(user.getId())
                .username(user.getName())
                .password(user.getPasswordHash())
                .build();
        return jwtUtil.generateToken(principal, user.getId());
    }

    private HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        return httpClient.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }
}
