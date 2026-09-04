package dev.langchain4j.model.openaiofficial.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.batch.BatchItemResult;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchPagination;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialBatchChatModel;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialBatchChatModelIT {

    private static final Duration COMPLETION_TIMEOUT = Duration.ofMinutes(15);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(15);

    private final OpenAiOfficialBatchChatModel model = OpenAiOfficialBatchChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME)
            .maxCompletionTokens(20)
            .temperature(0.0)
            .batchMetadata(Map.of("source", "langchain4j-integration-test"))
            .build();

    private static BatchRequest<ChatRequest> batchOf(String... prompts) {
        List<ChatRequest> requests = java.util.Arrays.stream(prompts)
                .map(prompt ->
                        ChatRequest.builder().messages(UserMessage.from(prompt)).build())
                .toList();
        return new BatchRequest<>(requests);
    }

    @Test
    void should_submit_and_retrieve_results_in_submission_order() {
        BatchResponse<ChatResponse> submitted = model.submit(batchOf(
                "What is the capital of France? Answer with the city name only.",
                "What is 2+2? Answer with the number only."));

        assertThat(submitted.batchId()).isNotBlank();
        assertThat(submitted.state().isTerminal()).isFalse();

        BatchResponse<ChatResponse> completed = awaitTerminalState(submitted.batchId());

        assertThat(completed.state()).isEqualTo(BatchState.SUCCEEDED);
        assertThat(completed.results()).hasSize(2);
        assertThat(completed.results()).allMatch(BatchItemResult::isSuccess);
        assertThat(completed.responses().get(0).aiMessage().text()).containsIgnoringCase("Paris");
        assertThat(completed.responses().get(1).aiMessage().text()).contains("4");
        assertThat(completed.responses().get(0).metadata().tokenUsage().totalTokenCount())
                .isPositive();
    }

    @Test
    void should_cancel_batch() {
        BatchResponse<ChatResponse> submitted = model.submit(batchOf("Reply with exactly one word: GAMMA"));

        model.cancel(submitted.batchId());

        BatchResponse<ChatResponse> cancelled = model.retrieve(submitted.batchId());
        assertThat(cancelled.state()).isIn(BatchState.RUNNING, BatchState.CANCELLED);
    }

    @Test
    void should_list_batches() {
        model.submit(batchOf("Reply with exactly one word: DELTA"));

        BatchPage<ChatResponse> page = model.list(new BatchPagination(1, null));

        assertThat(page.batches()).hasSize(1);
        assertThat(page.batches().get(0).batchId()).isNotBlank();
    }

    private BatchResponse<ChatResponse> awaitTerminalState(String batchId) {
        try {
            return await().atMost(COMPLETION_TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .until(
                            () -> model.retrieve(batchId),
                            response -> response.state().isTerminal());
        } catch (ConditionTimeoutException e) {
            model.cancel(batchId);
            return Assumptions.abort("Batch " + batchId + " did not reach a terminal state within " + COMPLETION_TIMEOUT
                    + "; OpenAI allows up to 24h, so this run is inconclusive rather than failed");
        }
    }
}
