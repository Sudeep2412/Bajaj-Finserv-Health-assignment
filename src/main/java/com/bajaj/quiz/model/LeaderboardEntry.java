package com.bajaj.quiz.model;

/**
 * A single leaderboard entry for submission.
 */
public record LeaderboardEntry(
        String participant,
        int totalScore
) implements Comparable<LeaderboardEntry> {

    @Override
    public int compareTo(LeaderboardEntry other) {
        // Descending by totalScore, then alphabetical by participant name
        int scoreCmp = Integer.compare(other.totalScore, this.totalScore);
        return scoreCmp != 0 ? scoreCmp : this.participant.compareTo(other.participant);
    }
}
