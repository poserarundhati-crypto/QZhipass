package org.microsoft.qintelipass.logins;

import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

public class EmailPasswordStrategy implements ILoginStrategy {
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public EmailPasswordStrategy(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String getType() {
        return "EMAIL_PWD";
    }

    @Override
    public ResponseBody<User> authenticate(Map<String, Object> params) {
        String email = readString(params, "email");
        String password = readPassword(params);

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return ResponseBody.<User>builder()
                    .success(false)
                    .message("Email and password should not be null.")
                    .build();
        }

        User user = userService.getUserByEmail(email);
        if (user == null) {
            return ResponseBody.<User>builder()
                    .success(false)
                    .message("User not found.")
                    .build();
        }

        if (!UserStatus.NORMAL.equals(user.getStatus())) {
            return ResponseBody.<User>builder()
                    .success(false)
                    .message("Your account is not active")
                    .build();
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return ResponseBody.<User>builder()
                    .success(false)
                    .message("Wrong password.")
                    .build();
        }

        return ResponseBody.<User>builder()
                .success(true)
                .message("Login Successful.")
                .payload(user)
                .build();
    }

    private String readString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    private String readPassword(Map<String, Object> params) {
        Object value = params.get("password");
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}
