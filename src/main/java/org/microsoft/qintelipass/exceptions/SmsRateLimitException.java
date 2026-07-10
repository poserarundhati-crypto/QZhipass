package org.microsoft.qintelipass.exceptions;

public class SmsRateLimitException extends RuntimeException {
    public SmsRateLimitException(String message) {
        super(message);
    }
}
