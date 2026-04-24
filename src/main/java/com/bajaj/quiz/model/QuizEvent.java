package com.bajaj.quiz.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Immutable representation of a single quiz event (score entry).
 * <p>
 * The natural deduplication key is {@code (roundId, participant)}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuizEvent(
        String roundId,
        String participant,
        int score
) {

    /**
     * Composite deduplication key.
     * Two events with the same key are considered duplicates.
     */
    public String deduplicationKey() {
        return roundId + "::" + participant;
    }
}
