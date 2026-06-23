package com.mdm.assistant.service;

import com.mdm.assistant.exception.ApiServiceException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ApiProtectionServiceTest {

    private ApiProtectionService apiProtectionService;
    private AtomicInteger callCounter;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RetryRegistry retryRegistry;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        retryRegistry = RetryRegistry.ofDefaults();
        TokenMonitorService tokenMonitorService = new TokenMonitorService();
        apiProtectionService = new ApiProtectionService(circuitBreakerRegistry, retryRegistry, tokenMonitorService);
        callCounter = new AtomicInteger(0);
    }

    @Test
    @DisplayName("成功调用应该返回结果")
    void testSuccessfulCall() {
        String result = apiProtectionService.executeWithProtection("test-session", () -> {
            callCounter.incrementAndGet();
            return "Success";
        });

        assertEquals("Success", result);
        assertEquals(1, callCounter.get());
    }

    @Test
    @DisplayName("单次失败后应该重试")
    void testRetryOnFailure() {
        callCounter.set(0);

        assertThrows(ApiServiceException.class, () -> {
            apiProtectionService.executeWithProtection("test-session", () -> {
                int count = callCounter.incrementAndGet();
                if (count < 3) {
                    throw new RuntimeException("Simulated failure " + count);
                }
                return "Success after retry";
            });
        });

        assertEquals(3, callCounter.get());
    }

    @Test
    @DisplayName("三次失败后应该停止调用并抛出异常")
    void testMaxRetriesExceeded() {
        callCounter.set(0);

        ApiServiceException exception = assertThrows(ApiServiceException.class, () -> {
            apiProtectionService.executeWithProtection("test-session", () -> {
                callCounter.incrementAndGet();
                throw new RuntimeException("Always fails");
            });
        });

        assertEquals(3, exception.getAttemptCount());
        assertFalse(exception.isCircuitBreakerTriggered());
    }

    @Test
    @DisplayName("熔断器打开时应该拒绝调用")
    void testCircuitBreakerRejectsCalls() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("llmApi");

        circuitBreaker.transitionToOpenState();

        ApiServiceException exception = assertThrows(ApiServiceException.class, () -> {
            apiProtectionService.executeWithProtection("test-session", () -> {
                return "Should not be called";
            });
        });

        assertTrue(exception.isCircuitBreakerTriggered());
        assertEquals(3, exception.getAttemptCount());
    }

    @Test
    @DisplayName("isCircuitBreakerOpen应该正确反映状态")
    void testCircuitBreakerStateCheck() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("llmApi");

        assertFalse(apiProtectionService.isCircuitBreakerOpen());

        circuitBreaker.transitionToOpenState();

        assertTrue(apiProtectionService.isCircuitBreakerOpen());
    }

    @Test
    @DisplayName("getCircuitBreakerState应该返回正确状态")
    void testGetCircuitBreakerState() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("llmApi");

        assertEquals(CircuitBreaker.State.CLOSED, apiProtectionService.getCircuitBreakerState());

        circuitBreaker.transitionToOpenState();

        assertEquals(CircuitBreaker.State.OPEN, apiProtectionService.getCircuitBreakerState());
    }
}
