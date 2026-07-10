package org.microsoft.qintelipass.exceptions;

public class AgentNotFoundException extends RuntimeException {
    public AgentNotFoundException() {
        super("Agent不存在或无权操作");
    }
}
