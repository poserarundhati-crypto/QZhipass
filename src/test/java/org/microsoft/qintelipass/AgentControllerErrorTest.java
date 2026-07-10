package org.microsoft.qintelipass;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.controllers.AgentController;
import org.microsoft.qintelipass.exceptions.AgentExceptionHandler;
import org.microsoft.qintelipass.security.AuthenticatedUser;
import org.microsoft.qintelipass.services.AgentService;
import org.microsoft.qintelipass.services.TokenUsageService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

@ExtendWith(MockitoExtension.class)
class AgentControllerErrorTest {
    private static final long USER_ID = 101L;
    private static final long AGENT_ID = 9001L;

    @Mock
    private TokenUsageService tokenUsageService;

    @Mock
    private AgentService agentService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AgentController controller = new AgentController(tokenUsageService, agentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AgentExceptionHandler())
                .build();

        AuthenticatedUser principal = AuthenticatedUser.builder()
                .userId(USER_ID)
                .username("agent-owner")
                .password("not-a-real-password-hash")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void databaseFailureReturnsStable500ResponseWithoutInternalDetails() throws Exception {
        when(agentService.deleteAgent(USER_ID, AGENT_ID))
                .thenThrow(new DataAccessResourceFailureException(
                        "SQL table agents failed with api_key_encrypted"));

        MvcResult result = mockMvc.perform(delete("/api/v1/agent/{agentId}", AGENT_ID)).andReturn();

        assertEquals(500, result.getResponse().getStatus());
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFalse(response.path("success").asBoolean());
        assertEquals("Agent操作失败，请稍后重试", response.path("message").asText());
        assertFalse(result.getResponse().getContentAsString().contains("agents"));
        assertFalse(result.getResponse().getContentAsString().contains("api_key"));
    }
}
