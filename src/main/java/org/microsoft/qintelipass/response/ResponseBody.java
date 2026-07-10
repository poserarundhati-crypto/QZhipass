package org.microsoft.qintelipass.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@Builder
@AllArgsConstructor
public class ResponseBody<T> {
    private boolean success;
    private String message;
    private T payload;
}