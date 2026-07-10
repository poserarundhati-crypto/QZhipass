package org.microsoft.qintelipass.controllers;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.dtos.AgentDeleteConfirmationDTO;
import org.microsoft.qintelipass.dtos.AgentDeleteResultDTO;
import org.microsoft.qintelipass.dtos.AgentDetailDTO;
import org.microsoft.qintelipass.dtos.AgentListDTO;
import org.microsoft.qintelipass.dtos.UserTokenUsageDTO;
import org.microsoft.qintelipass.request.AgentUpdateRequest;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.security.SecurityUtil;
import org.microsoft.qintelipass.services.AgentService;
import org.microsoft.qintelipass.services.TokenUsageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {
    private final TokenUsageService tokenUsageService;
    private final AgentService agentService;

    public AgentController(TokenUsageService tokenUsageService, AgentService agentService) {
        this.tokenUsageService = tokenUsageService;
        this.agentService = agentService;
    }

    @GetMapping
    public ResponseEntity<ResponseBody<AgentListDTO>> listAgents(
            @RequestParam(value = "q", required = false) String keyword) {
        AgentListDTO result = agentService.listAgents(currentUserId(), keyword);
        return ok("success", result);
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<ResponseBody<AgentDetailDTO>> getAgent(@PathVariable Long agentId) {
        AgentDetailDTO result = agentService.getAgent(currentUserId(), agentId);
        return ok("success", result);
    }

    @PutMapping("/{agentId}")
    public ResponseEntity<ResponseBody<AgentDetailDTO>> updateAgent(
            @PathVariable Long agentId,
            @RequestBody AgentUpdateRequest request) {
        AgentDetailDTO result = agentService.updateAgent(currentUserId(), agentId, request);
        return ok("Agent更新成功", result);
    }

    @GetMapping("/{agentId}/delete-confirmation")
    public ResponseEntity<ResponseBody<AgentDeleteConfirmationDTO>> getDeleteConfirmation(
            @PathVariable Long agentId) {
        AgentDeleteConfirmationDTO result = agentService.getDeleteConfirmation(currentUserId(), agentId);
        return ok("success", result);
    }

    @DeleteMapping("/{agentId}")
    public ResponseEntity<ResponseBody<AgentDeleteResultDTO>> deleteAgent(@PathVariable Long agentId) {
        AgentDeleteResultDTO result = agentService.deleteAgent(currentUserId(), agentId);
        String message = result.alreadyDeleted() ? "Agent已删除" : "Agent删除成功";
        return ok(message, result);
    }

    @PostMapping("/call")
    public ResponseEntity<ResponseBody<Map<String, Object>>> callLegacyAgent() {
        return processAgentCall(currentUserId(), null);
    }

    @PostMapping("/{agentId}/call")
    public ResponseEntity<ResponseBody<Map<String, Object>>> callAgentById(@PathVariable Long agentId) {
        Long userId = currentUserId();
        agentService.requireActiveAgent(userId, agentId);
        return processAgentCall(userId, agentId);
    }

    private ResponseEntity<ResponseBody<Map<String, Object>>> processAgentCall(Long userId, Long agentId) {
        int mockToken = 10003;

        log.info("Agent call requested by authenticated user: {}, agentId: {}, estimated tokens: {}",
                userId, agentId, mockToken);

        boolean canProceed = tokenUsageService.checkTokenLimit(userId);
        if (!canProceed) {
            UserTokenUsageDTO usage = tokenUsageService.getUserTokenUsage(userId);
            return ResponseEntity.badRequest().body(
                    ResponseBody.<Map<String, Object>>builder()
                            .success(false)
                            .message(String.format("Token limit exceeded! Used: %d / %d",
                                    usage.getTokenUsed(), usage.getTokenLimit()))
                            .build()
            );
        }

        log.info("Agent call processing for user: {}", userId);

        tokenUsageService.recordTokenUsage(userId, mockToken);

        UserTokenUsageDTO updatedUsage = tokenUsageService.getUserTokenUsage(userId);

        Map<String, Object> result = Map.of(
                "response", "Agent response placeholder",
                "tokensUsed", mockToken,
                "currentUsage", updatedUsage
        );

        return ResponseEntity.ok(ResponseBody.<Map<String, Object>>builder()
                .success(true)
                .message("Agent call completed successfully")
                .payload(result)
                .build());
    }

    private Long currentUserId() {
        SecurityUtil.requireAuthentication();
        return SecurityUtil.getCurrentUserId();
    }

    private <T> ResponseEntity<ResponseBody<T>> ok(String message, T payload) {
        return ResponseEntity.ok(ResponseBody.<T>builder()
                .success(true)
                .message(message)
                .payload(payload)
                .build());
    }
}
