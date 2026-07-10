package org.microsoft.qintelipass.services;

import org.springframework.stereotype.Service;

@Service
public interface ISmsService {
    void sendSmsCode(String phoneNumber);

    boolean consumeSmsCode(String phoneNumber, String smsCode);

    boolean isValidPhone(String phoneNumber);
}
