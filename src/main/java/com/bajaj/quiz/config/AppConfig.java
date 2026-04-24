package com.bajaj.quiz.config;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Infrastructure beans — WebClient and Resilience4j Retry.
 */
@Configuration
@EnableConfigurationProperties(QuizProperties.class)
public class AppConfig {

    @Bean
    public WebClient webClient(QuizProperties props) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.http().connectTimeoutMs())
                .responseTimeout(Duration.ofMillis(props.http().readTimeoutMs()));

        return WebClient.builder()
                .baseUrl(props.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
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
