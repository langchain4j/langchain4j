package dev.langchain4j.model.googleai;

import static dev.langchain4j.model.batch.BatchJobState.CANCELLED;
import static dev.langchain4j.model.batch.BatchJobState.FAILED;
import static dev.langchain4j.model.batch.BatchJobState.PENDING;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiBatchImageModelIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
    private static final String MODEL_NAME = "gemini-2.5-flash-image";

    @Test
    void should_create_batch_with_valid_image_requests() {
        // given
        var subject = GoogleAiGeminiBatchImageModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(MODEL_NAME)
                .logRequestsAndResponses(true)
                .build();

        var displayName = "Test Image Batch";
        var priority = 1L;
        var prompts = List.of("A simple red circle on white background", "A simple blue square on white background");

        // when
        var response = subject.createBatch(displayName, priority, prompts);

        // then
        assertThat(response.isIncomplete()).isTrue();
        assertThat(response.batchName().value()).startsWith("batches/");
        assertThat(response.state()).isEqualTo(PENDING);

        // cleanup
        subject.cancelJob(response.batchName());
    }

    @Test
    void should_cancel_image_batch_job() {
        // given
        var subject = GoogleAiGeminiBatchImageModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(MODEL_NAME)
                .logRequestsAndResponses(true)
                .build();

        var prompts = List.of("A green triangle");
        var createResponse = subject.createBatch("Cancel Test", null, prompts);

        // when
        subject.cancelJob(createResponse.batchName());

        // then
        var retrieveResponse = subject.retrieveResults(createResponse.batchName());
        assertThat(retrieveResponse.isError()).isTrue();
        assertThat(retrieveResponse.state()).isIn(CANCELLED, FAILED);
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_GOOGLE_AI_GEMINI_BATCH");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
