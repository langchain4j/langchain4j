package dev.langchain4j.model.mistralai;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchPagination;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiBatchChatModelIT {

    private final MistralAiBatchChatModel model = MistralAiBatchChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName("mistral-small-latest")
            .maxTokens(32)
            .build();

    @Test
    void should_submit_poll_and_retrieve_batch_results() throws InterruptedException {
        BatchResponse<ChatResponse> submitted = model.submit(new BatchRequest<>(List.of(
                ChatRequest.builder()
                        .messages(UserMessage.from("What is the capital of France? Answer in one word."))
                        .build(),
                ChatRequest.builder()
                        .messages(UserMessage.from("What is the capital of Germany? Answer in one word."))
                        .build())));

        assertThat(submitted.batchId()).isNotBlank();
        assertThat(submitted.state()).isIn(BatchState.PENDING, BatchState.RUNNING);
        assertThat(submitted.results()).isEmpty();

        BatchResponse<ChatResponse> retrieved = pollUntilTerminal(submitted.batchId(), Duration.ofMinutes(15));

        assertThat(retrieved.state()).isEqualTo(BatchState.SUCCEEDED);
        assertThat(retrieved.results()).hasSize(2);
        assertThat(retrieved.responses()).isNotEmpty();
        retrieved.results().stream()
                .filter(result -> result.isSuccess())
                .forEach(result ->
                        assertThat(result.response().aiMessage().text()).isNotBlank());
    }

    @Test
    void should_list_batches() {
        BatchPage<ChatResponse> page = model.list(new BatchPagination(5, null));

        assertThat(page.batches()).isNotNull();
    }

    @Test
    void should_submit_and_cancel_batch() {
        BatchResponse<ChatResponse> submitted = model.submit(new BatchRequest<>(List.of(ChatRequest.builder()
                .messages(UserMessage.from("Write a very long essay about the ocean."))
                .build())));

        model.cancel(submitted.batchId());

        BatchResponse<ChatResponse> afterCancel = model.retrieve(submitted.batchId());
        assertThat(afterCancel.state()).isNotNull();
    }

    private BatchResponse<ChatResponse> pollUntilTerminal(String batchId, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        BatchResponse<ChatResponse> response = model.retrieve(batchId);
        while (!response.state().isTerminal() && System.nanoTime() < deadline) {
            SECONDS.sleep(10);
            response = model.retrieve(batchId);
        }
        return response;
    }
}
