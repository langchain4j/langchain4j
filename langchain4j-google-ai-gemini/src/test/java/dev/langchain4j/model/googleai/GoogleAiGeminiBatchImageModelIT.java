package dev.langchain4j.model.googleai;

import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_CANCELLED;
import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_PENDING;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.googleai.BatchRequestResponse.BatchError;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchIncomplete;
import dev.langchain4j.model.googleai.GoogleAiGeminiBatchImageModel.ImageGenerationRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Disabled("Constantly getting 429, see the discussion here: https://github.com/langchain4j/langchain4j/pull/3942")
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
        var requests = List.of(
                new ImageGenerationRequest("A simple red circle on white background"),
                new ImageGenerationRequest("A simple blue square on white background"));

        // when
        var response = subject.createBatchInline(displayName, priority, requests);

        // then
        assertThat(response).isInstanceOf(BatchIncomplete.class);
        var incomplete = (BatchIncomplete<?>) response;
        assertThat(incomplete.batchName().value()).startsWith("batches/");
        assertThat(incomplete.state()).isEqualTo(BATCH_STATE_PENDING);

        // cleanup
        subject.cancelBatchJob(incomplete.batchName());
    }

    @Test
    void should_cancel_image_batch_job() {
        // given
        var subject = GoogleAiGeminiBatchImageModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(MODEL_NAME)
                .logRequestsAndResponses(true)
                .build();

        var requests = List.of(new ImageGenerationRequest("A green triangle"));
        var createResponse = (BatchIncomplete<?>) subject.createBatchInline("Cancel Test", null, requests);

        // when
        subject.cancelBatchJob(createResponse.batchName());

        // then
        var retrieveResponse = subject.retrieveBatchResults(createResponse.batchName());
        assertThat(retrieveResponse).isInstanceOf(BatchError.class);
        var error = (BatchError<?>) retrieveResponse;
        assertThat(error.state()).isEqualTo(BATCH_STATE_CANCELLED);
        assertThat(error.code()).isEqualTo(13);
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_GOOGLE_AI_GEMINI_BATCH");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
