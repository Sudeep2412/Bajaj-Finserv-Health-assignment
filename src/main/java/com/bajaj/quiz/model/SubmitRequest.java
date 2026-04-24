package com.bajaj.quiz.model;

import java.util.List;

/**
 * Request payload for {@code POST /quiz/submit}.
 */
public record SubmitRequest(
        String regNo,
        List<LeaderboardEntry> leaderboard
) {}
