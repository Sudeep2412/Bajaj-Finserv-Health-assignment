package com.bajaj.quiz.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuizEventTest {

    @Test
    @DisplayName("Deduplication key format is roundId::participant")
    void deduplicationKeyFormat() {
        QuizEvent event = new QuizEvent("R1", "Alice", 10);
        assertEquals("R1::Alice", event.deduplicationKey());
    }

    @Test
    @DisplayName("Same roundId + participant → same key")
    void sameKeyForDuplicates() {
        QuizEvent e1 = new QuizEvent("R1", "Alice", 10);
        QuizEvent e2 = new QuizEvent("R1", "Alice", 10);
        assertEquals(e1.deduplicationKey(), e2.deduplicationKey());
    }

    @Test
    @DisplayName("Different roundId → different key")
    void differentKeyForDifferentRound() {
        QuizEvent e1 = new QuizEvent("R1", "Alice", 10);
        QuizEvent e2 = new QuizEvent("R2", "Alice", 10);
        assertNotEquals(e1.deduplicationKey(), e2.deduplicationKey());
    }

    @Test
    @DisplayName("Different participant → different key")
    void differentKeyForDifferentParticipant() {
        QuizEvent e1 = new QuizEvent("R1", "Alice", 10);
        QuizEvent e2 = new QuizEvent("R1", "Bob", 10);
        assertNotEquals(e1.deduplicationKey(), e2.deduplicationKey());
    }
}
