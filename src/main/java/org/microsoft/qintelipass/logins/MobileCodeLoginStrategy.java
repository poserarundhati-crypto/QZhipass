package org.microsoft.qintelipass.logins;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.ISmsService;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.util.StringUtils;

import java.util.Map;

@Slf4j
public class MobileCodeLoginStrategy implements ILoginStrategy {
    private final UserService userService;
    private final ISmsService smsService;

    public MobileCodeLoginStrategy(UserService userService, ISmsService smsService) {
        this.userService = userService;
        this.smsService = smsService;
    }

    @Override
    public String getType() {
        return "smsLogin";
    }

    @Override
    public ResponseBody<User> authenticate(Map<String, Object> params) {
        String phone = readString(params, "phone_number", "phone", "mobile");
        String smsCode = readString(params, "sms", "smsCode", "sms_code");
        log.info("SMS login request received.");
        if (!smsService.isValidPhone(phone)
                || !StringUtils.hasText(smsCode)
                || !smsCode.matches("\\d{6}")) {
            return ResponseBody
                    .<User>builder()
                    .success(false)
                    .message("Invalid phone number or verification code.")
                    .build();
        }

        if (!smsService.consumeSmsCode(phone, smsCode)) {
            return ResponseBody.<User>builder()
                    .success(false)
                    .message("Invalid phone number or verification code.")
                    .build();
        }

        User user = userService.getUserByPhone(phone);
        if (user == null) {
            return ResponseBody.<User>builder()
                    .success(false)
                    .message("Invalid phone number or verification code.")
                    .build();
        }

        if (!UserStatus.NORMAL.equals(user.getStatus())) {
            return ResponseBody
                    .<User>builder()
                    .success(false)
                    .message("Your account is not active")
                    .build();
        }
        
        return ResponseBody.<User>builder()
                .success(true)
                .message("Login Successful.")
                .payload(user)
                .build();
    }

    private String readString(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value instanceof String text && StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        return null;
    }
}
