package dev.langchain4j.model.google.genai;

import static dev.langchain4j.model.batch.BatchState.PENDING;
import static dev.langchain4j.model.batch.BatchState.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.batch.BatchPagination;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiBatchChatModelIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Test
    void test_create_and_cancel_batch() throws InterruptedException {
        GoogleGenAiBatchChatModel batchModel = GoogleGenAiBatchChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash")
                .build();

        var requests = List.of(
                ChatRequest.builder()
                        .messages(UserMessage.from("What is the capital of France?"))
                        .build(),
                ChatRequest.builder()
                        .messages(UserMessage.from("What is the capital of Germany?"))
                        .build());

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

    @Test
    void test_list_batch_jobs_with_pagination() {
        GoogleGenAiBatchChatModel batchModel = GoogleGenAiBatchChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash")
                .build();

        var firstPage = batchModel.list(new BatchPagination(1, null));
        assertThat(firstPage).isNotNull();
        assertThat(firstPage.batches()).isNotNull();

        if (firstPage.nextPageToken() != null) {
            var secondPage = batchModel.list(new BatchPagination(1, firstPage.nextPageToken()));
            assertThat(secondPage).isNotNull();
            assertThat(secondPage.batches()).isNotNull();
        }
    }
}
