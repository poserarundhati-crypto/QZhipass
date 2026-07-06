package org.microsoft.qintelipass.configs;

import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.logins.EmailPasswordStrategy;
import org.microsoft.qintelipass.logins.MobileCodeLoginStrategy;
import org.microsoft.qintelipass.logins.MobilePasswordStrategy;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class LoginStrategyConfig {
    @Bean("smsStrategy")
    public ILoginStrategy smsLoginStrategy() {
        return new MobileCodeLoginStrategy();
    }
    @Bean("MOBILE_PWD")
    public ILoginStrategy mobilePassword(UserService userService, PasswordEncoder passwordEncoder){
        return new MobilePasswordStrategy(userService, passwordEncoder);
    }

    @Bean("EMAIL_PWD")
    public ILoginStrategy emailPassword(UserService userService, PasswordEncoder passwordEncoder){
        return new EmailPasswordStrategy(userService, passwordEncoder);
    }
}
