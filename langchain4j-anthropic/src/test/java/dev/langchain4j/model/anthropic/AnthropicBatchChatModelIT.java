package dev.langchain4j.model.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchPagination;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicBatchChatModelIT {

    private static final String ANTHROPIC_API_KEY = System.getenv("ANTHROPIC_API_KEY");

    private final AnthropicBatchChatModel model = AnthropicBatchChatModel.builder()
            .apiKey(ANTHROPIC_API_KEY)
            .modelName("claude-haiku-4-5-20251001")
            .maxTokens(16)
            .build();

    @Test
    void should_submit_retrieve_and_list_a_batch() {
        BatchResponse<ChatResponse> submitted = model.submit(new BatchRequest<>(List.of(ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of France? Answer with one word."))
                .build())));

        assertThat(submitted.batchId()).isNotBlank();
        assertThat(submitted.state()).isIn(BatchState.PENDING, BatchState.RUNNING, BatchState.SUCCEEDED);

        BatchResponse<ChatResponse> retrieved = model.retrieve(submitted.batchId());
        assertThat(retrieved.batchId()).isEqualTo(submitted.batchId());
        assertThat(retrieved.state()).isNotNull();

        BatchPage<ChatResponse> page = model.list(new BatchPagination(20, null));
        assertThat(page.batches()).anyMatch(batch -> batch.batchId().equals(submitted.batchId()));

        try {
            model.cancel(submitted.batchId());
        } catch (RuntimeException ignored) {
            // best-effort cleanup; the batch may already have ended and can no longer be cancelled
        }
    }
}
