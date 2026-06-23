package com.mdm.assistant.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiServiceException.class)
    public ResponseEntity<Map<String, Object>> handleApiServiceException(ApiServiceException e) {
        log.error("API Service Exception after {} attempts: {}", e.getAttemptCount(), e.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "服务不可用：" + e.getMessage()); 
        response.put("errorCode", "SERVICE_UNAVAILABLE");
        response.put("timestamp", LocalDateTime.now().toString());

        if (e.isCircuitBreakerTriggered()) {
            response.put("message", "服务繁忙（熔断器已打开），请等待 30 秒后重试。");
        }

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleServiceUnavailableException(ServiceUnavailableException e) {
        log.error("Service Unavailable: {}", e.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", e.getMessage());
        response.put("errorCode", "SERVICE_UNAVAILABLE");
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception e) {
        log.error("Unexpected error: ", e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "发生了意外错误，请稍后再试。");
        response.put("errorCode", "INTERNAL_ERROR");
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
