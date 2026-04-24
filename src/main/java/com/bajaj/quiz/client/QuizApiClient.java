package com.bajaj.quiz.client;

import com.bajaj.quiz.config.QuizProperties;
import com.bajaj.quiz.model.PollResponse;
import com.bajaj.quiz.model.SubmitRequest;
import com.bajaj.quiz.model.SubmitResponse;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.function.Supplier;

/**
 * Synchronous HTTP client for the quiz validator API.
 * <p>
 * Uses Spring Boot 3.2+ {@link RestClient} — a modern, fluent, <b>synchronous</b>
 * HTTP client. Unlike the reactive {@code WebClient}, there is no paradigm clash:
 * no {@code .block()} calls, no Reactor subscriptions, no Netty event loop.
 * <p>
 * All outgoing calls are wrapped with Resilience4j {@link Retry} to handle
 * transient network failures with exponential backoff.
 */
@Component
public class QuizApiClient {

    private static final Logger log = LoggerFactory.getLogger(QuizApiClient.class);

    private final RestClient restClient;
    private final Retry retry;
    private final QuizProperties props;

    public QuizApiClient(RestClient restClient, Retry retry, QuizProperties props) {
        this.restClient = restClient;
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

            PollResponse response = restClient.get()
                    .uri("/quiz/messages?regNo={regNo}&poll={poll}", props.regNo(), pollIndex)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(PollResponse.class);

            if (response == null) {
                throw new RuntimeException("Null response for poll " + pollIndex);
            }

            log.debug("← Poll {} returned {} events", pollIndex, response.events().size());
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

            SubmitResponse response = restClient.post()
                    .uri("/quiz/submit")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(SubmitResponse.class);

            if (response == null) {
                throw new RuntimeException("Null response from submit endpoint");
            }
            return response;
        });

        try {
            return supplier.get();
        } catch (RestClientException e) {
            log.error("✗ Submit failed: {}", e.getMessage());
            throw e;
        }
    }
}
