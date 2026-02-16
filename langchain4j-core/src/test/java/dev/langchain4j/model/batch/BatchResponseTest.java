package dev.langchain4j.model.batch;

import static dev.langchain4j.model.batch.BatchState.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BatchResponseTest {
    private static final BatchId BATCH_NAME = new BatchId("batches/test-batch");

    @Test
    void isIncomplete_shouldReturnTrue_whenStateIsPending() {
        var response = new BatchResponse<>(BATCH_NAME, PENDING, List.of(), null);
        assertThat(response.isInProgress()).isTrue();
    }

    @Test
    void isIncomplete_shouldReturnTrue_whenStateIsRunning() {
        var response = new BatchResponse<>(BATCH_NAME, RUNNING, List.of(), null);
        assertThat(response.isInProgress()).isTrue();
    }

    @Test
    void isIncomplete_shouldReturnFalse_whenStateIsSucceeded() {
        var response = new BatchResponse<>(BATCH_NAME, SUCCEEDED, List.of(), null);
        assertThat(response.isInProgress()).isFalse();
    }

    @Test
    void isIncomplete_shouldReturnFalse_whenStateIsFailed() {
        var response = new BatchResponse<>(BATCH_NAME, FAILED, List.of(), null);
        assertThat(response.isInProgress()).isFalse();
    }

    @Test
    void isIncomplete_shouldReturnFalse_whenStateIsExpired() {
        var response = new BatchResponse<>(BATCH_NAME, EXPIRED, List.of(), null);
        assertThat(response.isInProgress()).isFalse();
    }

    @Test
    void isSuccess_shouldReturnTrue_whenStateIsSucceeded() {
        var response = new BatchResponse<>(BATCH_NAME, SUCCEEDED, List.of(), null);
        assertThat(response.hasSucceeded()).isTrue();
    }

    @Test
    void isSuccess_shouldReturnFalse_whenStateIsFailed() {
        var response = new BatchResponse<>(BATCH_NAME, FAILED, List.of(), null);
        assertThat(response.hasSucceeded()).isFalse();
    }

    @Test
    void isSuccess_shouldReturnFalse_whenStateIsPending() {
        var response = new BatchResponse<>(BATCH_NAME, PENDING, List.of(), null);
        assertThat(response.hasSucceeded()).isFalse();
    }

    @Test
    void isError_shouldReturnTrue_whenStateIsFailed() {
        var response = new BatchResponse<>(BATCH_NAME, FAILED, List.of(), null);
        assertThat(response.hasFailed()).isTrue();
    }

    @Test
    void isError_shouldReturnFalse_whenStateIsSucceeded() {
        var response = new BatchResponse<>(BATCH_NAME, SUCCEEDED, List.of(), null);
        assertThat(response.hasFailed()).isFalse();
    }

    @Test
    void isError_shouldReturnFalse_whenStateIsExpired() {
        var response = new BatchResponse<>(BATCH_NAME, EXPIRED, List.of(), null);
        assertThat(response.hasFailed()).isFalse();
    }

    @Test
    void shouldStoreResponsesAndErrors() {
        var responses = List.of("response1", "response2");
        var errors = List.of(new BatchError(400, "Bad request", null));

        var response = new BatchResponse<>(BATCH_NAME, SUCCEEDED, responses, errors);

        assertThat(response.responses()).containsExactly("response1", "response2");
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().get(0).code()).isEqualTo(400);
        assertThat(response.errors().get(0).message()).isEqualTo("Bad request");
    }
}
