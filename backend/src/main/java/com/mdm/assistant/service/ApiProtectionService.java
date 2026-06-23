package com.mdm.assistant.service;

import com.mdm.assistant.exception.ApiServiceException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class ApiProtectionService {

    private static final Logger log = LoggerFactory.getLogger(ApiProtectionService.class);

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final TokenMonitorService tokenMonitorService;

    public ApiProtectionService(CircuitBreakerRegistry circuitBreakerRegistry,
                                RetryRegistry retryRegistry,
                                TokenMonitorService tokenMonitorService) {
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("llmApi");
        this.retry = retryRegistry.retry("llmApi");
        this.tokenMonitorService = tokenMonitorService;
    }

    public <T> T executeWithProtection(String sessionId, Supplier<T> supplier) {
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            log.warn("Circuit breaker is OPEN, rejecting request");
            throw new ApiServiceException("服务暂时过载，请 30 秒后重试。", 3, true);
        }

        try {
            Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker,
                    Retry.decorateSupplier(retry, supplier));

            return decoratedSupplier.get();
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            log.error("Circuit breaker rejected call");
            throw new ApiServiceException("服务暂时过载，请 30 秒后重试。", 3, true);
        } catch (Exception e) {
            int attempts = retry.getRetryConfig().getMaxAttempts();
            log.error("API call failed after {} attempts: {}", attempts, e.getMessage());

            if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                throw new ApiServiceException("服务暂时过载，请 30 秒后重试。", attempts, true);
            }

            throw new ApiServiceException("抱歉，AI 服务暂时不可用，请稍后再试。", attempts, false);
        }
    }

    public boolean isCircuitBreakerOpen() {
        return circuitBreaker.getState() == CircuitBreaker.State.OPEN;
    }

    public CircuitBreaker.State getCircuitBreakerState() {
        return circuitBreaker.getState();
    }
}
