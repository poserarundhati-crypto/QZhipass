package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.exceptions.SmsRateLimitException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.regex.Pattern;

@Service
public class SmsServiceImpl implements ISmsService {
    private static final String CODE_KEY_PREFIX = "auth:sms:code:";
    private static final String COOLDOWN_KEY_PREFIX = "auth:sms:cooldown:";
    private static final String HOURLY_COUNTER_KEY_PREFIX = "auth:sms:hourly:";
    private static final String ATTEMPTS_KEY_PREFIX = "auth:sms:attempts:";
    private static final Duration SMS_CODE_TTL = Duration.ofMinutes(5);
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration HOURLY_WINDOW = Duration.ofHours(1);
    private static final int MAX_SENDS_PER_HOUR = 5;
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RedisService redisService;

    public SmsServiceImpl(RedisService redisService) {
        this.redisService = redisService;
    }

    @Override
    public void sendSmsCode(String phoneNumber) {
        String normalizedPhone = normalizePhone(phoneNumber);
        String randomCode = this.getRandomCode(6);
        long result = redisService.storeSmsCodeIfAllowed(
                cooldownKey(normalizedPhone),
                hourlyCounterKey(normalizedPhone),
                codeKey(normalizedPhone),
                attemptsKey(normalizedPhone),
                randomCode,
                SMS_CODE_TTL,
                SEND_COOLDOWN,
                MAX_SENDS_PER_HOUR,
                HOURLY_WINDOW);
        if (result == 0L) {
            throw new SmsRateLimitException("Please wait before requesting another SMS code.");
        }
        if (result == -1L) {
            throw new SmsRateLimitException("Too many SMS codes requested. Please try again later.");
        }
        if (result != 1L) {
            throw new IllegalStateException("Unexpected SMS storage result");
        }
    }

    @Override
    public boolean consumeSmsCode(String phoneNumber, String smsCode) {
        String normalizedPhone = normalizePhone(phoneNumber);
        if (smsCode == null || !smsCode.matches("\\d{6}")) {
            return false;
        }
        return redisService.consumeSmsCode(
                codeKey(normalizedPhone),
                attemptsKey(normalizedPhone),
                smsCode,
                MAX_VERIFICATION_ATTEMPTS);
    }

    public String getRandomCode(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("SMS code length must be positive");
        }
        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < length; index++) {
            sb.append(SECURE_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    @Override
    public boolean isValidPhone(String phoneNumber) {
        return phoneNumber != null && MOBILE_PATTERN.matcher(phoneNumber.trim()).matches();
    }

    private String normalizePhone(String phoneNumber) {
        if (!isValidPhone(phoneNumber)) {
            throw new IllegalArgumentException("Invalid phone number format.");
        }
        return phoneNumber.trim();
    }

    private String codeKey(String phoneNumber) {
        return CODE_KEY_PREFIX + clusterSlot(phoneNumber);
    }

    private String cooldownKey(String phoneNumber) {
        return COOLDOWN_KEY_PREFIX + clusterSlot(phoneNumber);
    }

    private String hourlyCounterKey(String phoneNumber) {
        return HOURLY_COUNTER_KEY_PREFIX + clusterSlot(phoneNumber);
    }

    private String attemptsKey(String phoneNumber) {
        return ATTEMPTS_KEY_PREFIX + clusterSlot(phoneNumber);
    }

    private String clusterSlot(String phoneNumber) {
        return "{" + phoneNumber + "}";
    }
}
