package com.bajaj.quiz.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * API response from {@code GET /quiz/messages}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PollResponse(
        String regNo,
        String setId,
        int pollIndex,
        List<QuizEvent> events
) {}
