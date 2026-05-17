package dev.langchain4j.model.google.genai;

import static dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchJobState.JOB_STATE_PENDING;
import static dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchJobState.JOB_STATE_RUNNING;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.google.genai.GoogleGenAiBatchImageModel.ImageGenerationRequest;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchIncomplete;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchName;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiBatchImageModelIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Test
    void test_create_and_cancel_batch() throws InterruptedException {
        GoogleGenAiBatchImageModel batchModel = GoogleGenAiBatchImageModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-3.1-flash-image-preview") // Or another appropriate model
                .build();

        var requests = List.of(
                new ImageGenerationRequest("A picture of a cat"), new ImageGenerationRequest("A picture of a dog"));

        var response = batchModel.createBatchInline("Test Image Batch", 1L, requests);

        assertThat(response).isInstanceOf(BatchIncomplete.class);
        BatchIncomplete<?> incomplete = (BatchIncomplete<?>) response;
        assertThat(incomplete.name().value()).startsWith("batches/");
        assertThat(incomplete.state()).isIn(JOB_STATE_PENDING, JOB_STATE_RUNNING);

        BatchName batchName = incomplete.name();

        // Retrieve
        var retrieved = batchModel.retrieveBatchResults(batchName);
        assertThat(retrieved).isNotNull();

        // Cancel
        batchModel.cancelBatchJob(batchName);

        // Delete
        batchModel.deleteBatchJob(batchName);
    }
}
