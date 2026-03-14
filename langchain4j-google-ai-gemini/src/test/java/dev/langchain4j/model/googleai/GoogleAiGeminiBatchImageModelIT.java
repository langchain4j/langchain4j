package dev.langchain4j.model.googleai;

import static dev.langchain4j.model.batch.BatchState.CANCELLED;
import static dev.langchain4j.model.batch.BatchState.FAILED;
import static dev.langchain4j.model.batch.BatchState.PENDING;
import static org.assertj.core.api.Assertions.assertThat;

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
    void should_submit_with_valid_image_requests() {
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
        var response = subject.submit(GeminiBatchRequest.from(prompts, displayName, priority));

        // then
        assertThat(response.isInProgress()).isTrue();
        assertThat(response.batchId().value()).startsWith("batches/");
        assertThat(response.state()).isEqualTo(PENDING);

        // cleanup
        subject.cancel(response.batchId());
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
        var response = subject.submit(GeminiBatchRequest.from(prompts, "Cancel test"));

        // when
        subject.cancel(response.batchId());

        // then
        var retrieveResponse = subject.retrieve(response.batchId());
        assertThat(retrieveResponse.hasFailed()).isTrue();
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
