package com.bajaj.quiz.service;

import com.bajaj.quiz.model.LeaderboardEntry;
import com.bajaj.quiz.model.QuizEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link DeduplicationEngine}.
 * <p>
 * Tests cover: basic dedup, cross-poll dedup, aggregation correctness,
 * leaderboard sorting, and edge cases (empty input, single event, etc.).
 */
class DeduplicationEngineTest {

    private DeduplicationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DeduplicationEngine();
    }

    @Nested
    @DisplayName("Deduplication")
    class Dedup {

        @Test
        @DisplayName("Should accept unique events")
        void acceptsUniqueEvents() {
            List<QuizEvent> events = List.of(
                    new QuizEvent("R1", "Alice", 10),
                    new QuizEvent("R1", "Bob", 20),
                    new QuizEvent("R2", "Alice", 15)
            );

            int accepted = engine.ingest(events);

            assertEquals(3, accepted);
            assertEquals(3, engine.uniqueCount());
            assertEquals(0, engine.duplicateCount());
        }

        @Test
        @DisplayName("Should reject duplicates within same batch")
        void rejectsDuplicatesWithinBatch() {
            List<QuizEvent> events = List.of(
                    new QuizEvent("R1", "Alice", 10),
                    new QuizEvent("R1", "Alice", 10)  // duplicate
            );

            int accepted = engine.ingest(events);

            assertEquals(1, accepted);
            assertEquals(1, engine.uniqueCount());
            assertEquals(1, engine.duplicateCount());
        }

        @Test
        @DisplayName("Should reject duplicates across batches (cross-poll)")
        void rejectsDuplicatesAcrossBatches() {
            engine.ingest(List.of(new QuizEvent("R1", "Alice", 10)));
            int accepted = engine.ingest(List.of(new QuizEvent("R1", "Alice", 10)));

            assertEquals(0, accepted);
            assertEquals(1, engine.uniqueCount());
            assertEquals(1, engine.duplicateCount());
        }

        @Test
        @DisplayName("Should distinguish events by roundId")
        void distinguishesByRoundId() {
            engine.ingest(List.of(
                    new QuizEvent("R1", "Alice", 10),
                    new QuizEvent("R2", "Alice", 20)  // different round = not duplicate
            ));

            assertEquals(2, engine.uniqueCount());
            assertEquals(0, engine.duplicateCount());
        }

        @Test
        @DisplayName("Should handle empty event list")
        void handlesEmptyList() {
            int accepted = engine.ingest(List.of());

            assertEquals(0, accepted);
            assertEquals(0, engine.uniqueCount());
        }
    }

    @Nested
    @DisplayName("Aggregation & Leaderboard")
    class Aggregation {

        @Test
        @DisplayName("Should aggregate scores per participant")
        void aggregatesScoresCorrectly() {
            engine.ingest(List.of(
                    new QuizEvent("R1", "Alice", 10),
                    new QuizEvent("R2", "Alice", 30),
                    new QuizEvent("R1", "Bob", 50)
            ));

            List<LeaderboardEntry> leaderboard = engine.buildLeaderboard();

            assertEquals(2, leaderboard.size());

            // Bob (50) should be first, Alice (40) second
            assertEquals("Bob", leaderboard.get(0).participant());
            assertEquals(50, leaderboard.get(0).totalScore());
            assertEquals("Alice", leaderboard.get(1).participant());
            assertEquals(40, leaderboard.get(1).totalScore());
        }

        @Test
        @DisplayName("Should sort by score descending, then alphabetically")
        void sortsCorrectly() {
            engine.ingest(List.of(
                    new QuizEvent("R1", "Charlie", 100),
                    new QuizEvent("R1", "Alice", 100),    // same score as Charlie
                    new QuizEvent("R1", "Bob", 50)
            ));

            List<LeaderboardEntry> leaderboard = engine.buildLeaderboard();

            // Alice and Charlie tied at 100 → alphabetical
            assertEquals("Alice", leaderboard.get(0).participant());
            assertEquals("Charlie", leaderboard.get(1).participant());
            assertEquals("Bob", leaderboard.get(2).participant());
        }

        @Test
        @DisplayName("Should compute correct total score")
        void computesTotalScore() {
            engine.ingest(List.of(
                    new QuizEvent("R1", "Alice", 10),
                    new QuizEvent("R2", "Alice", 30),
                    new QuizEvent("R1", "Bob", 50),
                    new QuizEvent("R1", "Bob", 50)  // duplicate — should be ignored
            ));

            assertEquals(90, engine.computeTotalScore());
        }

        @Test
        @DisplayName("Total score should exclude duplicates")
        void totalScoreExcludesDuplicates() {
            // Simulates the exact scenario from the assignment spec
            engine.ingest(List.of(new QuizEvent("R1", "Alice", 10)));
            engine.ingest(List.of(new QuizEvent("R1", "Alice", 10)));  // dup from poll 3

            assertEquals(10, engine.computeTotalScore()); // NOT 20
        }
    }

    @Nested
    @DisplayName("Reset")
    class Reset {

        @Test
        @DisplayName("Should clear all state on reset")
        void resetsCleanly() {
            engine.ingest(List.of(new QuizEvent("R1", "Alice", 10)));
            engine.reset();

            assertEquals(0, engine.uniqueCount());
            assertEquals(0, engine.duplicateCount());
            assertEquals(0, engine.totalIngestedCount());
            assertEquals(0, engine.computeTotalScore());
            assertTrue(engine.buildLeaderboard().isEmpty());
        }
    }
}
