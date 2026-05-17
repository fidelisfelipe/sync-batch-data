package com.fidelis.syncbatch.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;

/**
 * Programmatic Resilience4j configuration.
 * Properties-based config in application.yml takes precedence; these beans
 * serve as a typed fallback and are used for dependency injection in services.
 */
@Slf4j
@Configuration
public class ResilienceConfig {

    public static final String EXTERNAL_CLIENT_CB    = "externalClient";
    public static final String EXTERNAL_CLIENT_RETRY = "externalClient";

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallRateThreshold(80)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(IOException.class, RuntimeException.class)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);

        registry.getEventPublisher().onEntryAdded(event ->
                log.info("CircuitBreaker registered: {}", event.getAddedEntry().getName()));

        return registry;
    }

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(IOException.class, RuntimeException.class)
                .build();

        return RetryRegistry.of(config);
    }
}
