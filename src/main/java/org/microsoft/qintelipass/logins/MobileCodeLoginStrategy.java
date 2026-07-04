package org.microsoft.qintelipass.logins;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.StringRedisService;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class MobileCodeLoginStrategy implements ILoginStrategy {
    @Autowired
    private StringRedisService redisService;
    
    @Autowired
    private UserService userService;
    
    public boolean validate(String phone, String smsCode) {
        return phone == null || smsCode == null || phone.length() != 11 || smsCode.length() != 6;
    }

    @Override
    public String getType() {
        return "smsLogin";
    }

    @Override
    public ResponseBody authenticate(Map<String, Object> params) {
        String phone = (String) params.get("phone_number");
        String smsCode = (String) params.get("sms");
        log.info("User phone: {}, User smsCode: {}", phone, smsCode);
        
        if (smsCode == null || phone == null){
            return ResponseBody.builder().success(false).message("smsCode or phone number could not be NULL.").build();
        }
        if (this.validate(phone, smsCode)){
            return ResponseBody.builder().success(false).message("Invalid smsCode or phone.").build();
        }
        
        User user = userService.getUserByPhone(phone);
        if (user == null) {
            return ResponseBody.builder().success(false).message("User not found.").build();
        }

        if (user != null && UserStatus.DEACTIVATED.equals(user.getStatus())) {
            return ResponseBody.builder().success(false).message("Your account has been deactivated").build();
        }
        
        String targetSmsCode = redisService.getValue(phone);

        if (targetSmsCode != null) {
            if (targetSmsCode.equals(smsCode)){
                return ResponseBody.builder().success(true).message("Login Successful.").payload(user).build();
            }
        }
        return ResponseBody.builder().success(false).message("Wrong smsCode.").build();
    }
}
