package com.mdm.assistant.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(3)
                .failureRateThreshold(100)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(1)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);

        CircuitBreaker circuitBreaker = registry.circuitBreaker("llmApi");
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    log.warn("Circuit breaker state transition: {} -> {}",
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState());
                })
                .onFailureRateExceeded(event -> {
                    log.error("Circuit breaker failure rate exceeded: {}", event.getFailureRate());
                });

        return registry;
    }

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .retryExceptions(Exception.class)
                .intervalFunction(attempt -> (long) Math.pow(2, attempt - 1) * 1000L)
                .build();

        RetryRegistry registry = RetryRegistry.of(config);

        Retry retry = registry.retry("llmApi");
        retry.getEventPublisher()
                .onRetry(event -> {
                    log.warn("Retrying LLM API call, attempt {}",
                            event.getNumberOfRetryAttempts());
                })
                .onError(event -> {
                    log.error("Retry error after {} attempts: {}",
                            event.getNumberOfRetryAttempts(),
                            event.getLastThrowable().getMessage());
                });

        return registry;
    }
}
