package com.bajaj.quiz.runner;

import com.bajaj.quiz.model.SubmitResponse;
import com.bajaj.quiz.service.QuizPipelineOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Application entry-point runner.
 * <p>
 * Executes the full pipeline on startup and exits with code 0 on success, 1 on failure.
 */
@Component
public class PipelineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PipelineRunner.class);

    private final QuizPipelineOrchestrator orchestrator;

    public PipelineRunner(QuizPipelineOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void run(String... args) {
        try {
            SubmitResponse result = orchestrator.execute();

            if (!result.wasSuccessful()) {
                log.error("Pipeline completed but submission was REJECTED. " +
                        "Submitted={}",
                        result.submittedTotal());
                System.exit(1);
            }

            log.info("Pipeline completed successfully.");
        } catch (Exception e) {
            log.error("Pipeline failed with exception: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
