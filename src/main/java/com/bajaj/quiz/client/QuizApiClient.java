package com.bajaj.quiz.client;

import com.bajaj.quiz.config.QuizProperties;
import com.bajaj.quiz.model.PollResponse;
import com.bajaj.quiz.model.SubmitRequest;
import com.bajaj.quiz.model.SubmitResponse;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.function.Supplier;

/**
 * HTTP client for the quiz validator API.
 * <p>
 * All outgoing calls are wrapped with Resilience4j {@link Retry} to handle
 * transient network failures with exponential backoff.
 */
@Component
public class QuizApiClient {

    private static final Logger log = LoggerFactory.getLogger(QuizApiClient.class);

    private final WebClient webClient;
    private final Retry retry;
    private final QuizProperties props;

    public QuizApiClient(WebClient webClient, Retry retry, QuizProperties props) {
        this.webClient = webClient;
        this.retry = retry;
        this.props = props;
    }

    /**
     * Polls the validator for a specific poll index.
     *
     * @param pollIndex 0-based poll index (0–9)
     * @return deserialized poll response
     */
    public PollResponse poll(int pollIndex) {
        Supplier<PollResponse> supplier = Retry.decorateSupplier(retry, () -> {
            log.debug("→ GET /quiz/messages?regNo={}&poll={}", props.regNo(), pollIndex);

            PollResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/quiz/messages")
                            .queryParam("regNo", props.regNo())
                            .queryParam("poll", pollIndex)
                            .build())
                    .retrieve()
                    .bodyToMono(PollResponse.class)
                    .block();

            if (response == null) {
                throw new RuntimeException("Null response for poll " + pollIndex);
            }

            log.debug("← Poll {} returned {} events (set={})",
                    pollIndex, response.events().size(), response.setId());
            return response;
        });

        try {
            return supplier.get();
        } catch (Exception e) {
            log.error("✗ Poll {} failed after {} retries: {}",
                    pollIndex, props.retry().maxAttempts(), e.getMessage());
            throw new RuntimeException("Poll " + pollIndex + " exhausted retries", e);
        }
    }

    /**
     * Submits the final leaderboard to the validator.
     *
     * @param request the submission payload
     * @return validator's verdict
     */
    public SubmitResponse submit(SubmitRequest request) {
        Supplier<SubmitResponse> supplier = Retry.decorateSupplier(retry, () -> {
            log.debug("→ POST /quiz/submit with {} entries", request.leaderboard().size());

            SubmitResponse response = webClient.post()
                    .uri("/quiz/submit")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(SubmitResponse.class)
                    .block();

            if (response == null) {
                throw new RuntimeException("Null response from submit endpoint");
            }
            return response;
        });

        try {
            return supplier.get();
        } catch (WebClientResponseException e) {
            log.error("✗ Submit failed — HTTP {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }
}
