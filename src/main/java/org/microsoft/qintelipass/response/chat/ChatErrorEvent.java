package org.microsoft.qintelipass.response.chat;

public record ChatErrorEvent(String code, String message, String requestId) {
}
