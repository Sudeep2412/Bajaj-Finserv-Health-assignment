package com.bajaj.quiz.service;

import com.bajaj.quiz.model.LeaderboardEntry;
import com.bajaj.quiz.model.QuizEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Core deduplication and aggregation engine.
 * <p>
 * Uses a {@link ConcurrentHashMap} keyed by {@code (roundId::participant)} to guarantee
 * that duplicate events across polls are processed exactly once, regardless of delivery order.
 * <p>
 * <b>Why ConcurrentHashMap?</b> While the current pipeline is sequential, this design is
 * future-proof for parallel polling scenarios without requiring synchronization changes.
 */
@Service
public class DeduplicationEngine {

    private static final Logger log = LoggerFactory.getLogger(DeduplicationEngine.class);

    /**
     * Stores the first occurrence of each unique event by its composite key.
     * Any subsequent events with the same key are silently discarded.
     */
    private final ConcurrentHashMap<String, QuizEvent> uniqueEvents = new ConcurrentHashMap<>();

    private int totalIngested = 0;
    private int totalDuplicates = 0;

    /**
     * Ingests a batch of events from a single poll, deduplicating on the fly.
     *
     * @param events list of events from a poll response
     * @return number of new (non-duplicate) events accepted from this batch
     */
    public int ingest(List<QuizEvent> events) {
        int newCount = 0;

        for (QuizEvent event : events) {
            totalIngested++;
            String key = event.deduplicationKey();

            // putIfAbsent is atomic — returns null only if this is a new key
            QuizEvent existing = uniqueEvents.putIfAbsent(key, event);

            if (existing == null) {
                newCount++;
                log.trace("  ✓ NEW  [{}] {} → {}", event.roundId(), event.participant(), event.score());
            } else {
                totalDuplicates++;
                log.trace("  ✗ DUP  [{}] {} (ignored)", event.roundId(), event.participant());
            }
        }

        return newCount;
    }

    /**
     * Aggregates all deduplicated events into a sorted leaderboard.
     * <p>
     * Pipeline: group by participant → sum scores → sort descending → collect.
     *
     * @return immutable sorted leaderboard
     */
    public List<LeaderboardEntry> buildLeaderboard() {
        return uniqueEvents.values().stream()
                .collect(Collectors.groupingBy(
                        QuizEvent::participant,
                        Collectors.summingInt(QuizEvent::score)
                ))
                .entrySet().stream()
                .map(e -> new LeaderboardEntry(e.getKey(), e.getValue()))
                .sorted()  // Uses LeaderboardEntry.compareTo (desc score, alpha name)
                .toList();
    }

    /**
     * Computes the grand total score across all participants.
     */
    public int computeTotalScore() {
        return uniqueEvents.values().stream()
                .mapToInt(QuizEvent::score)
                .sum();
    }

    /**
     * @return number of unique events after deduplication
     */
    public int uniqueCount() {
        return uniqueEvents.size();
    }

    /**
     * @return total raw events ingested (before deduplication)
     */
    public int totalIngestedCount() {
        return totalIngested;
    }

    /**
     * @return number of duplicate events discarded
     */
    public int duplicateCount() {
        return totalDuplicates;
    }

    /**
     * Resets the engine for a fresh run.
     */
    public void reset() {
        uniqueEvents.clear();
        totalIngested = 0;
        totalDuplicates = 0;
    }
}
