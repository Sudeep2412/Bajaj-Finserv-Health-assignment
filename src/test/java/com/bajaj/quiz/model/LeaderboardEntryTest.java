package com.bajaj.quiz.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class LeaderboardEntryTest {

    @Test
    @DisplayName("Sorts by score descending")
    void sortsByScoreDescending() {
        var entries = List.of(
                new LeaderboardEntry("A", 10),
                new LeaderboardEntry("B", 30),
                new LeaderboardEntry("C", 20)
        );

        var sorted = entries.stream().sorted().toList();

        assertEquals("B", sorted.get(0).participant());
        assertEquals("C", sorted.get(1).participant());
        assertEquals("A", sorted.get(2).participant());
    }

    @Test
    @DisplayName("Alphabetical tiebreak for same score")
    void alphabeticalTiebreak() {
        var entries = List.of(
                new LeaderboardEntry("Charlie", 100),
                new LeaderboardEntry("Alice", 100)
        );

        var sorted = entries.stream().sorted().toList();

        assertEquals("Alice", sorted.get(0).participant());
        assertEquals("Charlie", sorted.get(1).participant());
    }
}
