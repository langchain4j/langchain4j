package dev.langchain4j.model.google.genai;

import static dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchJobState.JOB_STATE_PENDING;
import static dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchJobState.JOB_STATE_RUNNING;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchIncomplete;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchName;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiBatchEmbeddingModelIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Test
    void test_create_and_cancel_batch() throws InterruptedException {
        GoogleGenAiBatchEmbeddingModel batchModel = GoogleGenAiBatchEmbeddingModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-embedding-2")
                .build();

        var requests = List.of(
                TextSegment.from("What is the capital of France?"),
                TextSegment.from("What is the capital of Germany?"));

        var response = batchModel.createBatchInline("Test Embedding Batch", 1L, requests);

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
