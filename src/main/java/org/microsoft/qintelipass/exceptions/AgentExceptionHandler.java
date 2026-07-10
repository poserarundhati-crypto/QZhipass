package org.microsoft.qintelipass.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.controllers.AgentController;
import org.microsoft.qintelipass.response.ResponseBody;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice(assignableTypes = AgentController.class)
public class AgentExceptionHandler {
    @ExceptionHandler({InvalidAgentRequestException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ResponseBody<Void>> handleBadRequest(Exception exception) {
        String message = exception instanceof InvalidAgentRequestException
                ? exception.getMessage()
                : "请求参数格式无效";
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(AgentNotFoundException.class)
    public ResponseEntity<ResponseBody<Void>> handleNotFound(AgentNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ResponseBody<Void>> handleUnauthorized(SecurityException exception) {
        return error(HttpStatus.UNAUTHORIZED, "未登录或登录已失效");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ResponseBody<Void>> handleDatabaseFailure(DataAccessException exception) {
        log.error("Agent database operation failed");
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Agent操作失败，请稍后重试");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ResponseBody<Void>> handleUnexpectedFailure(RuntimeException exception) {
        log.error("Unexpected Agent operation failure");
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Agent操作失败，请稍后重试");
    }

    private ResponseEntity<ResponseBody<Void>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ResponseBody.<Void>builder()
                .success(false)
                .message(message)
                .payload(null)
                .build());
    }
}
