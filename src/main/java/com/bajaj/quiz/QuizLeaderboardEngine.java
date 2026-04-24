package com.bajaj.quiz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Quiz Leaderboard Engine.
 * <p>
 * This application polls a distributed quiz validator API, deduplicates
 * event data across polls, aggregates participant scores, and submits
 * the final leaderboard — all with built-in resilience (retry + backoff).
 */
@SpringBootApplication
public class QuizLeaderboardEngine {

    public static void main(String[] args) {
        SpringApplication.run(QuizLeaderboardEngine.class, args);
    }
}
