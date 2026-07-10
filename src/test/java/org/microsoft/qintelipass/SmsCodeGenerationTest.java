package org.microsoft.qintelipass;

import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.services.SmsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class SmsCodeGenerationTest {
    @Autowired
    public SmsServiceImpl smsService;
    @Test
    public void testCodeValid(){
        for (int i = 0; i < 100; i++) {
            assertTrue(smsService.getRandomCode(6).matches("\\d{6}"));
        }
    }
}
