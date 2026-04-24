# Quiz Leaderboard Engine

A **resilient, production-grade data pipeline** built with Spring Boot 3.3 that polls a distributed quiz validator API, deduplicates event data across multiple polls, aggregates participant scores, and submits a verified leaderboard — all with built-in fault tolerance.

## Problem Statement

This application solves a real-world distributed systems challenge: a quiz show validator API delivers participant score events across 10 sequential polls, but **the same event data may appear in multiple polls** (simulating at-least-once delivery). The task is to:

1. Poll the API 10 times with mandatory 5-second intervals
2. **Deduplicate** events using composite key `(roundId, participant)`
3. **Aggregate** scores per participant
4. **Generate** a sorted leaderboard
5. **Submit** the result exactly once

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    QuizLeaderboardEngine                        │
│                    (Spring Boot Application)                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐    ┌────────────────────┐    ┌─────────────┐ │
│  │ PipelineRunner│───▶│QuizPipeline        │───▶│ QuizApi     │ │
│  │ (CLI Entry)   │    │Orchestrator        │    │ Client      │ │
│  └──────────────┘    │                    │    │ (RestClient) │ │
│                      │  POLL ──────────┐  │    └──────┬──────┘ │
│                      │  COLLECT        │  │           │        │
│                      │  DEDUPLICATE ◀──┘  │    ┌──────▼──────┐ │
│                      │  AGGREGATE         │    │ Resilience4j │ │
│                      │  SUBMIT ───────────┼───▶│ Retry       │ │
│                      └────────────────────┘    └─────────────┘ │
│                             │                                   │
│                      ┌──────▼──────────┐                       │
│                      │ Deduplication    │                       │
│                      │ Engine           │                       │
│                      │ (ConcurrentHash  │                       │
│                      │  Map + putIfAbs) │                       │
│                      └─────────────────┘                       │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Model Layer (Java 17 Records — Immutable)                   ││
│  │ QuizEvent │ PollResponse │ LeaderboardEntry │ Submit*       ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

## Key Design Decisions

### 1. ConcurrentHashMap with `putIfAbsent` for Deduplication
Rather than collecting all events and post-processing for duplicates, the engine uses `ConcurrentHashMap.putIfAbsent()` which is **atomic and O(1)**. This means:
- Duplicates are detected and discarded at ingestion time
- Memory usage is bounded to unique events only
- The data structure is inherently thread-safe (future-proof for parallel polling)

### 2. Java 17 Records for Domain Models
All DTOs (`QuizEvent`, `PollResponse`, `LeaderboardEntry`, etc.) are **immutable records** — no getters/setters boilerplate, built-in `equals()`/`hashCode()`, and clear data semantics.

### 3. Resilience4j Retry with Backoff
Every outgoing HTTP call is wrapped with Resilience4j `Retry`:
- **3 max attempts** per call
- **2-second base wait** with configurable exponential backoff
- Retries on all transient exceptions (network timeouts, 5xx errors)

### 4. Synchronous RestClient (Spring Boot 3.2+)
Uses `RestClient` — Spring's modern synchronous HTTP client — instead of the reactive `WebClient`:
- **No reactive-blocking paradigm clash** (no `.block()` calls on reactive streams)
- Backed by JDK's `HttpURLConnection` via `SimpleClientHttpRequestFactory` — zero external HTTP dependencies
- Configurable connection/read timeouts
- Lighter classpath (no Netty, no Reactor) → ~15% faster startup

### 5. Externalized Configuration
All pipeline parameters (regNo, timeouts, retry settings, poll delay) are externalized in `application.yml` and can be overridden via environment variables:
```bash
REG_NO=2024CS101 mvn spring-boot:run
```

## Project Structure

```
src/
├── main/java/com/bajaj/quiz/
│   ├── QuizLeaderboardEngine.java     # Spring Boot entry point
│   ├── config/
│   │   ├── QuizProperties.java        # Type-safe config (record)
│   │   └── AppConfig.java             # RestClient + Retry beans
│   ├── model/
│   │   ├── QuizEvent.java             # Score event with dedup key
│   │   ├── PollResponse.java          # API response DTO
│   │   ├── LeaderboardEntry.java      # Sorted leaderboard entry
│   │   ├── SubmitRequest.java         # Submission payload
│   │   └── SubmitResponse.java        # Validator verdict
│   ├── client/
│   │   └── QuizApiClient.java         # HTTP client with retry
│   ├── service/
│   │   ├── DeduplicationEngine.java   # Core dedup + aggregation
│   │   └── QuizPipelineOrchestrator.java  # Pipeline coordinator
│   └── runner/
│       └── PipelineRunner.java        # CLI runner (CommandLineRunner)
└── test/java/com/bajaj/quiz/
    ├── model/
    │   ├── QuizEventTest.java         # Dedup key tests
    │   └── LeaderboardEntryTest.java  # Sorting tests
    └── service/
        └── DeduplicationEngineTest.java  # Full dedup + aggregation tests
```

## Prerequisites

- **Java 17+** (LTS)
- **Maven 3.8+** (or use the included `mvnw` wrapper)

## Quick Start

### 1. Clone the repository
```bash
git clone https://github.com/Sudeep2412/Bajaj-Finserv-Health-assignment.git
cd Bajaj-Finserv-Health-assignment
```

### 2. Configure your registration number
Edit `src/main/resources/application.yml`:
```yaml
quiz:
  reg-no: "YOUR_REG_NO_HERE"
```
Or pass it as an environment variable:
```bash
set REG_NO=YOUR_REG_NO_HERE   # Windows
export REG_NO=YOUR_REG_NO_HERE # Linux/Mac
```

### 3. Run tests
```bash
mvn test
```

### 4. Run the pipeline
```bash
mvn spring-boot:run
```
Or with a custom reg number:
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--quiz.reg-no=YOUR_REG_NO"
```

### 5. Build executable JAR
```bash
mvn clean package -DskipTests
java -jar target/quiz-leaderboard-engine-1.0.0.jar
```

## Sample Output

```
╔══════════════════════════════════════════════════════════════╗
║           QUIZ LEADERBOARD ENGINE — PIPELINE START          ║
╠══════════════════════════════════════════════════════════════╣
║  Registration : RA2311026010202
║  Poll Count   : 10
║  Poll Delay   : 5000 ms
╚══════════════════════════════════════════════════════════════╝

─── Phase 1: Polling 10 times (5000ms delay) ────────────────────
  ✓ Poll  1/10 completed in 520ms — 2 events
  ✓ Poll  2/10 completed in  89ms — 1 events
  ...
  ✓ Poll 10/10 completed in 108ms — 2 events

╔══════════════════════════════════════════════════════════════╗
║                        LEADERBOARD                         ║
╠══════════════════════════════════════════════════════════════╣
║  🥇  1. Diana                                 470       ║
║  🥈  2. Ethan                                 455       ║
║  🥉  3. Fiona                                 440       ║
╠══════════════════════════════════════════════════════════════╣
║  TOTAL SCORE (all participants):              1365       ║
╚══════════════════════════════════════════════════════════════╝

📊 Dedup Stats: 15 ingested → 9 unique + 6 duplicates

╔══════════════════════════════════════════════════════════════╗
║                    SUBMISSION RESULT                        ║
╠══════════════════════════════════════════════════════════════╣
║  ✅ SUBMISSION ACCEPTED
║  Submitted Total : 1365
║  Total Polls Made: 31
║  Attempt Count   : 4
║  Pipeline Time   : 0m 46s 557ms
╚══════════════════════════════════════════════════════════════╝
```

## Testing

The project includes **16 unit tests** covering:

| Test Suite | Coverage |
|---|---|
| `DeduplicationEngineTest` | Dedup within batch, cross-batch, aggregation, sorting, total score, reset |
| `QuizEventTest` | Deduplication key format and uniqueness |
| `LeaderboardEntryTest` | Score-descending sort with alphabetical tiebreak |

Run all tests:
```bash
mvn test
```

## Configuration Reference

| Property | Default | Description |
|---|---|---|
| `quiz.reg-no` | `RA2311026010202` | Your registration number |
| `quiz.base-url` | `https://devapigw.vidalhealthtpa.com/srm-quiz-task` | Validator API base URL |
| `quiz.poll-count` | `10` | Number of sequential polls |
| `quiz.poll-delay-ms` | `5000` | Mandatory delay between polls (ms) |
| `quiz.retry.max-attempts` | `3` | Max retry attempts per HTTP call |
| `quiz.retry.wait-duration-ms` | `2000` | Base wait between retries (ms) |
| `quiz.http.connect-timeout-ms` | `5000` | HTTP connection timeout |
| `quiz.http.read-timeout-ms` | `10000` | HTTP read timeout |

## Tech Stack

| Component | Technology |
|---|---|
| Framework | Spring Boot 3.3.5 |
| HTTP Client | Spring RestClient (synchronous, JDK HttpURLConnection) |
| Resilience | Resilience4j (Retry) |
| Language | Java 17 (Records, Streams, Text Blocks) |
| Build | Maven |
| Testing | JUnit 5, Spring Boot Test |
