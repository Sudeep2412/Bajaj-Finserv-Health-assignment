package com.bajaj.quiz.service;

import com.bajaj.quiz.client.QuizApiClient;
import com.bajaj.quiz.config.QuizProperties;
import com.bajaj.quiz.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the full quiz pipeline:
 * <pre>
 *   POLL (×10) → COLLECT → DEDUPLICATE → AGGREGATE → SUBMIT
 * </pre>
 * <p>
 * Each poll is separated by a mandatory delay (configurable, default 5s).
 * All metrics are tracked and printed as a summary at the end.
 */
@Service
public class QuizPipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(QuizPipelineOrchestrator.class);

    private final QuizApiClient apiClient;
    private final DeduplicationEngine deduplicationEngine;
    private final QuizProperties props;

    public QuizPipelineOrchestrator(QuizApiClient apiClient,
                                    DeduplicationEngine deduplicationEngine,
                                    QuizProperties props) {
        this.apiClient = apiClient;
        this.deduplicationEngine = deduplicationEngine;
        this.props = props;
    }

    /**
     * Executes the complete pipeline and returns the submission response.
     */
    public SubmitResponse execute() {
        Instant pipelineStart = Instant.now();

        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║           QUIZ LEADERBOARD ENGINE — PIPELINE START          ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");
        log.info("║  Registration : {}",  props.regNo());
        log.info("║  Base URL     : {}",  props.baseUrl());
        log.info("║  Poll Count   : {}",  props.pollCount());
        log.info("║  Poll Delay   : {} ms", props.pollDelayMs());
        log.info("╚══════════════════════════════════════════════════════════════╝");

        // ─── Phase 1: Polling ─────────────────────────────────────────────
        List<PollResponse> rawResponses = pollAll();

        // ─── Phase 2: Deduplication & Ingestion ───────────────────────────
        deduplicationEngine.reset();
        for (PollResponse response : rawResponses) {
            int newEvents = deduplicationEngine.ingest(response.events());
            log.info("  Poll {} → {} events received, {} new, {} duplicates discarded",
                    response.pollIndex(),
                    response.events().size(),
                    newEvents,
                    response.events().size() - newEvents);
        }

        // ─── Phase 3: Aggregation ─────────────────────────────────────────
        List<LeaderboardEntry> leaderboard = deduplicationEngine.buildLeaderboard();
        int totalScore = deduplicationEngine.computeTotalScore();

        printLeaderboard(leaderboard, totalScore);

        // ─── Phase 4: Submission ──────────────────────────────────────────
        SubmitRequest submitRequest = new SubmitRequest(props.regNo(), leaderboard);
        SubmitResponse submitResponse = apiClient.submit(submitRequest);

        Duration elapsed = Duration.between(pipelineStart, Instant.now());
        printResult(submitResponse, elapsed);

        return submitResponse;
    }

    /**
     * Polls the API sequentially with mandatory delay between calls.
     */
    private List<PollResponse> pollAll() {
        List<PollResponse> responses = new ArrayList<>();

        log.info("─── Phase 1: Polling {} times ({}ms delay) ────────────────────",
                props.pollCount(), props.pollDelayMs());

        for (int i = 0; i < props.pollCount(); i++) {
            if (i > 0) {
                log.debug("  ⏳ Waiting {}ms before next poll...", props.pollDelayMs());
                sleep(props.pollDelayMs());
            }

            Instant pollStart = Instant.now();
            PollResponse response = apiClient.poll(i);
            long pollDurationMs = Duration.between(pollStart, Instant.now()).toMillis();

            responses.add(response);
            log.info("  ✓ Poll {}/{} completed in {}ms — {} events",
                    i + 1, props.pollCount(), pollDurationMs, response.events().size());
        }

        log.info("─── Polling complete. {} total responses collected. ────────────",
                responses.size());
        return responses;
    }

    /**
     * Pretty-prints the leaderboard to the console.
     */
    private void printLeaderboard(List<LeaderboardEntry> leaderboard, int totalScore) {
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║                        LEADERBOARD                         ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");
        log.info("║  {}",
                String.format("%-6s %-30s %10s       ║", "Rank", "Participant", "Score"));
        log.info("╠══════════════════════════════════════════════════════════════╣");

        int rank = 1;
        for (LeaderboardEntry entry : leaderboard) {
            String medal = switch (rank) {
                case 1 -> "🥇";
                case 2 -> "🥈";
                case 3 -> "🥉";
                default -> "  ";
            };
            log.info("║  {} {}",
                    medal, String.format("%2d. %-30s %10d       ║", rank, entry.participant(), entry.totalScore()));
            rank++;
        }

        log.info("╠══════════════════════════════════════════════════════════════╣");
        log.info("║  TOTAL SCORE (all participants): {}",
                String.format("%27d  ║", totalScore));
        log.info("╚══════════════════════════════════════════════════════════════╝");
        log.info("");
        log.info("📊 Dedup Stats: {} ingested → {} unique + {} duplicates",
                deduplicationEngine.totalIngestedCount(),
                deduplicationEngine.uniqueCount(),
                deduplicationEngine.duplicateCount());
        log.info("");
    }

    /**
     * Pretty-prints the submission result.
     */
    private void printResult(SubmitResponse response, Duration elapsed) {
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║                    SUBMISSION RESULT                        ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");

        if (response.wasSuccessful()) {
            log.info("║  ✅ SUBMISSION ACCEPTED");
        } else {
            log.warn("║  ❌ SUBMISSION REJECTED");
        }

        // Display actual API response fields
        if (response.submittedTotal() != null) {
            log.info("║  Submitted Total : {}", response.submittedTotal());
        }
        if (response.expectedTotal() != null) {
            log.info("║  Expected Total  : {}", response.expectedTotal());
        }
        if (response.totalPollsMade() != null) {
            log.info("║  Total Polls Made: {}", response.totalPollsMade());
        }
        if (response.attemptCount() != null) {
            log.info("║  Attempt Count   : {}", response.attemptCount());
        }
        if (response.isIdempotent() != null) {
            log.info("║  Idempotent      : {}", response.isIdempotent());
        }
        if (response.message() != null) {
            log.info("║  Message         : {}", response.message());
        }
        log.info("║  Pipeline Time   : {}", formatDuration(elapsed));
        log.info("╚══════════════════════════════════════════════════════════════╝");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Polling interrupted", e);
        }
    }

    private String formatDuration(Duration d) {
        long secs = d.getSeconds();
        long millis = d.toMillisPart();
        return String.format("%dm %ds %dms", secs / 60, secs % 60, millis);
    }
}
