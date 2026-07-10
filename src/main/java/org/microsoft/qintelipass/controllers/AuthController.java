package org.microsoft.qintelipass.controllers;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.CredentialManager;
import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.IRegisterable;
import org.microsoft.qintelipass.LoginStrategyFactory;
import org.microsoft.qintelipass.dtos.UserDTO;
import org.microsoft.qintelipass.exceptions.SmsRateLimitException;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.request.LoginRequest;
import org.microsoft.qintelipass.request.RegisterRequest;
import org.microsoft.qintelipass.response.ConversationResponse;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.ConversationService;
import org.microsoft.qintelipass.services.ISmsService;
import org.microsoft.qintelipass.services.UserDetailsServiceImpl;
import org.microsoft.qintelipass.util.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/portal")
public class AuthController {
    private final LoginStrategyFactory factory;
    private final ISmsService smsService;
    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final CredentialManager credentialManager;
    private final IRegisterable registerService;
    private final ConversationService conversationService;

    public AuthController(
            LoginStrategyFactory factory,
            ISmsService smsService,
            JwtUtil jwtUtil,
            UserDetailsServiceImpl userDetailsService,
            CredentialManager credentialManager,
            IRegisterable registerService,
            ConversationService conversationService) {
        this.factory = factory;
        this.smsService = smsService;
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.credentialManager = credentialManager;
        this.registerService = registerService;
        this.conversationService = conversationService;
    }

    @PostMapping("/login")
    public ResponseEntity<ResponseBody<?>> login(@RequestBody LoginRequest formData) {
        try {
            String loginType = formData == null ? null : formData.getLoginType();
            Map<String, Object> params = formData == null ? Map.of() : formData.effectiveParams();
            log.info("Login request received. loginType={}", loginType);

            ILoginStrategy strategy = factory.getStrategy(loginType);
            ResponseBody<User> authentication = strategy.authenticate(params);
            User user = authentication.getPayload();
            if (!authentication.isSuccess() || user == null) {
                return ResponseEntity.badRequest().body(authentication);
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getName());
            String accessToken = jwtUtil.generateToken(userDetails, user.getId());
            ConversationResponse conversation = conversationService.createInitialConversation(user.getId());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("user_id", user.getId().toString());
            payload.put("access_token", accessToken);
            payload.put("initialConversationId", conversation.id().toString());
            payload.put("conversation", conversation);

            return ResponseEntity.ok(ResponseBody.builder()
                    .success(true)
                    .message("Login Successful.")
                    .payload(payload)
                    .build());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ResponseBody.builder()
                    .success(false)
                    .message(exception.getMessage())
                    .build());
        } catch (DataAccessException exception) {
            log.error("Authentication verification storage is unavailable: {}",
                    exception.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ResponseBody.builder()
                    .success(false)
                    .message("Authentication service is temporarily unavailable.")
                    .build());
        }
    }

    @PostMapping({"/sendcode", "/send_code"})
    public ResponseEntity<ResponseBody<Void>> sendCode(@RequestBody Map<String, String> payload) {
        String phone = payload == null ? null : payload.get("phone");
        if (!smsService.isValidPhone(phone)) {
            return ResponseEntity.badRequest().body(ResponseBody.<Void>builder()
                    .success(false)
                    .message("Invalid phone number format.")
                    .build());
        }

        try {
            smsService.sendSmsCode(phone.trim());
            return ResponseEntity.ok(ResponseBody.<Void>builder()
                    .success(true)
                    .message("Sms code sent.")
                    .build());
        } catch (SmsRateLimitException exception) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ResponseBody.<Void>builder()
                    .success(false)
                    .message(exception.getMessage())
                    .build());
        } catch (DataAccessException exception) {
            log.error("SMS verification storage is unavailable: {}", exception.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ResponseBody.<Void>builder()
                    .success(false)
                    .message("SMS verification service is temporarily unavailable.")
                    .build());
        }
    }

    @DeleteMapping("/logout")
    public ResponseEntity<ResponseBody<Void>> logoutUser(
            @RequestHeader(value = "Authorization", required = false) String token) {
        if (!credentialManager.checkIfLogin(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ResponseBody.<Void>builder()
                    .success(false)
                    .message("Not logged in.")
                    .build());
        }

        return ResponseEntity.ok(ResponseBody.<Void>builder()
                .success(true)
                .message("OK")
                .build());
    }

    @PostMapping("/register")
    public ResponseEntity<ResponseBody<?>> register(@RequestBody RegisterRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(ResponseBody.builder()
                    .success(false)
                    .message("Registration information is incomplete or invalid.")
                    .build());
        }
        User registered = registerService.register(request, request.getPassword());
        if (registered == null) {
            return ResponseEntity.badRequest().body(ResponseBody.builder()
                    .success(false)
                    .message("Registration information is incomplete or invalid.")
                    .build());
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(registered.getName());
        String accessToken = jwtUtil.generateToken(userDetails, registered.getId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", registered.getId().toString());
        payload.put("access_token", accessToken);
        payload.put("user", UserDTO.fromUser(registered));
        return ResponseEntity.status(HttpStatus.CREATED).body(ResponseBody.builder()
                .success(true)
                .message("Registration successful.")
                .payload(payload)
                .build());
    }

}
