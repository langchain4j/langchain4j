package dev.langchain4j.model.google.genai;

import static dev.langchain4j.model.batch.BatchState.PENDING;
import static dev.langchain4j.model.batch.BatchState.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.batch.BatchRequest;
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

        var response = batchModel.submit(new BatchRequest<>(requests));

        assertThat(response).isNotNull();
        assertThat(response.batchId()).startsWith("batches/");
        assertThat(response.state()).isIn(PENDING, RUNNING);

        String batchId = response.batchId();

        // Retrieve
        var retrieved = batchModel.retrieve(batchId);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.batchId()).isEqualTo(batchId);

        // Cancel
        batchModel.cancel(batchId);

        // Delete
        batchModel.deleteBatchJob(batchId);
    }
}
