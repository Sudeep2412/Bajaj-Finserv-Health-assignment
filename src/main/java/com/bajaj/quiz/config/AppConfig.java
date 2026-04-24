package com.bajaj.quiz.config;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Infrastructure beans — RestClient and Resilience4j Retry.
 * <p>
 * Uses Spring Boot 3.2+ {@link RestClient} (synchronous, fluent HTTP client)
 * instead of WebFlux's {@code WebClient}. This avoids pulling in the entire
 * Reactor/Netty stack for what is fundamentally a sequential CLI pipeline.
 */
@Configuration
@EnableConfigurationProperties(QuizProperties.class)
public class AppConfig {

    /**
     * Configures a synchronous {@link RestClient} with connection and read timeouts.
     * <p>
     * Backed by {@link SimpleClientHttpRequestFactory} (JDK's {@code HttpURLConnection}),
     * which is the lightest-weight option — no Netty, no Apache HttpClient needed.
     */
    @Bean
    public RestClient restClient(QuizProperties props) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(props.http().connectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(props.http().readTimeoutMs()));

        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public Retry quizRetry(QuizProperties props) {
        var retryProps = props.retry();
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(retryProps.maxAttempts())
                .waitDuration(Duration.ofMillis(retryProps.waitDurationMs()))
                .retryExceptions(Exception.class)
                .build();
        return Retry.of("quizPollRetry", config);
    }
}
