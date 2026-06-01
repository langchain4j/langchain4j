package dev.langchain4j.model.batch;

import static dev.langchain4j.model.batch.BatchState.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BatchResponseTest {
    private static final String BATCH_NAME = "batches/test-batch";

    private static BatchResponse<String> responseWithState(BatchState state) {
        return BatchResponse.<String>builder().batchId(BATCH_NAME).state(state).build();
    }

    @Test
    void isIncomplete_shouldReturnTrue_whenStateIsPending() {
        assertThat(responseWithState(PENDING).isInProgress()).isTrue();
    }

    @Test
    void isIncomplete_shouldReturnTrue_whenStateIsRunning() {
        assertThat(responseWithState(RUNNING).isInProgress()).isTrue();
    }

    @Test
    void isIncomplete_shouldReturnFalse_whenStateIsSucceeded() {
        assertThat(responseWithState(SUCCEEDED).isInProgress()).isFalse();
    }

    @Test
    void isIncomplete_shouldReturnFalse_whenStateIsFailed() {
        assertThat(responseWithState(FAILED).isInProgress()).isFalse();
    }

    @Test
    void isIncomplete_shouldReturnFalse_whenStateIsExpired() {
        assertThat(responseWithState(EXPIRED).isInProgress()).isFalse();
    }

    @Test
    void isSuccess_shouldReturnTrue_whenStateIsSucceeded() {
        assertThat(responseWithState(SUCCEEDED).hasSucceeded()).isTrue();
    }

    @Test
    void isSuccess_shouldReturnFalse_whenStateIsFailed() {
        assertThat(responseWithState(FAILED).hasSucceeded()).isFalse();
    }

    @Test
    void isSuccess_shouldReturnFalse_whenStateIsPending() {
        assertThat(responseWithState(PENDING).hasSucceeded()).isFalse();
    }

    @Test
    void isError_shouldReturnTrue_whenStateIsFailed() {
        assertThat(responseWithState(FAILED).hasFailed()).isTrue();
    }

    @Test
    void isError_shouldReturnFalse_whenStateIsSucceeded() {
        assertThat(responseWithState(SUCCEEDED).hasFailed()).isFalse();
    }

    @Test
    void isError_shouldReturnFalse_whenStateIsExpired() {
        assertThat(responseWithState(EXPIRED).hasFailed()).isFalse();
    }

    @Test
    void responsesAndErrors_shouldBeDerivedFromResults() {
        var error = new BatchError(400, "Bad request", null);
        var response = BatchResponse.<String>builder()
                .batchId(BATCH_NAME)
                .state(SUCCEEDED)
                .results(List.of(
                        BatchItemResult.success("response1"),
                        BatchItemResult.success("response2"),
                        BatchItemResult.failure(error)))
                .build();

        assertThat(response.responses()).containsExactly("response1", "response2");
        assertThat(response.errors()).containsExactly(error);
        assertThat(response.errors().get(0).code()).isEqualTo(400);
        assertThat(response.errors().get(0).message()).isEqualTo("Bad request");
    }

    @Test
    void results_shouldPreserveOrderAndCorrelateOutcomesWithRequests() {
        var error = new BatchError(429, "Rate limited", null);
        var response = BatchResponse.<String>builder()
                .batchId(BATCH_NAME)
                .state(SUCCEEDED)
                .results(List.of(
                        BatchItemResult.success("first"),
                        BatchItemResult.failure(error),
                        BatchItemResult.success("third")))
                .build();

        var results = response.results();
        assertThat(results).hasSize(3);

        assertThat(results.get(0).isSuccess()).isTrue();
        assertThat(results.get(0).response()).isEqualTo("first");
        assertThat(results.get(0).error()).isNull();

        assertThat(results.get(1).isSuccess()).isFalse();
        assertThat(results.get(1).response()).isNull();
        assertThat(results.get(1).error()).isEqualTo(error);

        assertThat(results.get(2).isSuccess()).isTrue();
        assertThat(results.get(2).response()).isEqualTo("third");
    }

    @Test
    void shouldDefaultNullResultsToEmptyList() {
        var response = BatchResponse.<String>builder()
                .batchId(BATCH_NAME)
                .state(SUCCEEDED)
                .results(null)
                .build();

        assertThat(response.results()).isEmpty();
        assertThat(response.responses()).isEmpty();
        assertThat(response.errors()).isEmpty();
    }

    @Test
    void builder_shouldBuildEquivalentResponse() {
        List<BatchItemResult<String>> results =
                List.of(BatchItemResult.success("response1"), BatchItemResult.success("response2"));

        var built = BatchResponse.<String>builder()
                .batchId(BATCH_NAME)
                .state(SUCCEEDED)
                .results(results)
                .build();

        var expected = BatchResponse.<String>builder()
                .batchId(BATCH_NAME)
                .state(SUCCEEDED)
                .results(results)
                .build();

        assertThat(built).isEqualTo(expected);
        assertThat(built.batchId()).isEqualTo(BATCH_NAME);
        assertThat(built.state()).isEqualTo(SUCCEEDED);
        assertThat(built.responses()).containsExactly("response1", "response2");
    }

    @Test
    void builder_shouldDefaultMissingResultsToEmpty() {
        var built = BatchResponse.<String>builder()
                .batchId(BATCH_NAME)
                .state(PENDING)
                .build();

        assertThat(built.results()).isEmpty();
        assertThat(built.responses()).isEmpty();
        assertThat(built.errors()).isEmpty();
        assertThat(built.isInProgress()).isTrue();
    }
}
