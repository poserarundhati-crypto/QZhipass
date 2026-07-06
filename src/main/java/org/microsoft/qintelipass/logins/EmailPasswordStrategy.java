package org.microsoft.qintelipass.logins;

import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.UserService;
import org.microsoft.qintelipass.util.QZhiPasswordPattern;
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
        String email = (String) params.get("email");
        String password = (String) params.get("password");

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return ResponseBody.<User>builder()
                    .success(false)
                    .message("Email and password should not be null.")
                    .build();
        }

        if (!QZhiPasswordPattern.validate(password)) {
            return ResponseBody.<User>builder()
                    .success(false)
                    .message(QZhiPasswordPattern.REQUIREMENT_MESSAGE)
                    .build();
        }

        User user = userService.getUserByEmail(email);
        if (user == null) {
            return ResponseBody.<User>builder()
                    .success(false)
                    .message("User not found.")
                    .build();
        }

        if (UserStatus.DEACTIVATED.equals(user.getStatus())) {
            return ResponseBody.<User>builder()
                    .success(false)
                    .message("Your account has been deactivated")
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
}
