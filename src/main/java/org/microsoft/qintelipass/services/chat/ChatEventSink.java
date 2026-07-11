package org.microsoft.qintelipass.services.chat;

@FunctionalInterface
public interface ChatEventSink {
    void send(String eventName, Object data) throws Exception;
}
