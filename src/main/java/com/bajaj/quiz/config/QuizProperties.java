package com.bajaj.quiz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe externalized configuration for the quiz pipeline.
 * Bound from {@code application.yml} under the {@code quiz.*} namespace.
 */
@ConfigurationProperties(prefix = "quiz")
public record QuizProperties(
        String regNo,
        String baseUrl,
        int pollCount,
        long pollDelayMs,
        RetryProperties retry,
        HttpProperties http
) {
    public record RetryProperties(
            int maxAttempts,
            long waitDurationMs,
            double backoffMultiplier
    ) {}

    public record HttpProperties(
            int connectTimeoutMs,
            int readTimeoutMs
    ) {}
}
