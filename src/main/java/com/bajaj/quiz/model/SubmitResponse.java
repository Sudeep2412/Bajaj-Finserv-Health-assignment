package com.bajaj.quiz.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response from {@code POST /quiz/submit}.
 * <p>
 * The API may return different fields depending on correctness.
 * We model all known fields as nullable/optional to handle both cases.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubmitResponse(
        // Fields from the actual API response
        String regNo,
        Integer totalPollsMade,
        Integer submittedTotal,
        Integer attemptCount,

        // Fields documented in the spec (may appear on correct submissions)
        Boolean isCorrect,
        Boolean isIdempotent,
        Integer expectedTotal,
        String message
) {
    /**
     * Determines if the submission was successful.
     * Checks both the documented spec field and the actual API behavior.
     */
    public boolean wasSuccessful() {
        // If the API returns isCorrect explicitly, use it
        if (isCorrect != null) return isCorrect;
        // If we got a submittedTotal back, the API accepted it
        return submittedTotal != null && submittedTotal > 0;
    }
}
