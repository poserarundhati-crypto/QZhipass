package org.microsoft.qintelipass.services.chat;

public class ProviderCallException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private static final String SAFE_MESSAGE = "模型服务调用失败，请稍后重试。";

    public ProviderCallException() {
        super(SAFE_MESSAGE);
    }
}
