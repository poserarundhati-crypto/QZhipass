package org.microsoft.qintelipass.services.chat;

public record ChatOptions(double temperature, int numPredict) {
    public static ChatOptions defaults() {
        return new ChatOptions(0.7D, 2048);
    }
}
