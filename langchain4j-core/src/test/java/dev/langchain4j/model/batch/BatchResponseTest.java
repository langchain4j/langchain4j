package dev.langchain4j.model.batch;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.model.batch.BatchJobState.*;
import static org.assertj.core.api.Assertions.assertThat;

class BatchResponseTest {
    private static final BatchName BATCH_NAME = new BatchName("batches/test-batch");

    @Test
    void isIncomplete_shouldReturnTrue_whenStateIsPending() {
        var response = new BatchResponse<>(BATCH_NAME, BATCH_STATE_PENDING, List.of(), null);
        assertThat(response.isIncomplete()).isTrue();
    }

    @Test
    void isIncomplete_shouldReturnTrue_whenStateIsRunning() {
        var response = new BatchResponse<>(BATCH_NAME, BATCH_STATE_RUNNING, List.of(), null);
        assertThat(response.isIncomplete()).isTrue();
    }

    @Test
    void isIncomplete_shouldReturnFalse_whenStateIsSucceeded() {
        var response = new BatchResponse<>(BATCH_NAME, BATCH_STATE_SUCCEEDED, List.of(), null);
        assertThat(response.isIncomplete()).isFalse();
    }

    @Test
    void isIncomplete_shouldReturnFalse_whenStateIsFailed() {
        var response = new BatchResponse<>(BATCH_NAME, BATCH_STATE_FAILED, List.of(), null);
        assertThat(response.isIncomplete()).isFalse();
    }

    @Test
    void isIncomplete_shouldReturnFalse_whenStateIsExpired() {
        var response = new BatchResponse<>(BATCH_NAME, BATCH_STATE_EXPIRED, List.of(), null);
        assertThat(response.isIncomplete()).isFalse();
    }

    @Test
    void isSuccess_shouldReturnTrue_whenStateIsSucceeded() {
        var response = new BatchResponse<>(BATCH_NAME, BATCH_STATE_SUCCEEDED, List.of(), null);
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void isSuccess_shouldReturnFalse_whenStateIsFailed() {
        var response = new BatchResponse<>(BATCH_NAME, BATCH_STATE_FAILED, List.of(), null);
        assertThat(response.isSuccess()).isFalse();
    }

    @Test
    void isSuccess_shouldReturnFalse_whenStateIsPending() {
        var response = new BatchResponse<>(BATCH_NAME, BATCH_STATE_PENDING, List.of(), null);
        assertThat(response.isSuccess()).isFalse();
    }

    @Test
    void isError_shouldReturnTrue_whenStateIsFailed() {
        var response = new BatchResponse<>(BATCH_NAME, BATCH_STATE_FAILED, List.of(), null);
        assertThat(response.isError()).isTrue();
    }

    @Test
    void isError_shouldReturnFalse_whenStateIsSucceeded() {
        var response = new BatchResponse<>(BATCH_NAME, BATCH_STATE_SUCCEEDED, List.of(), null);
        assertThat(response.isError()).isFalse();
    }

    @Test
    void isError_shouldReturnFalse_whenStateIsExpired() {
        var response = new BatchResponse<>(BATCH_NAME, BATCH_STATE_EXPIRED, List.of(), null);
        assertThat(response.isError()).isFalse();
    }

    @Test
    void shouldStoreResponsesAndErrors() {
        var responses = List.of("response1", "response2");
        var errors = List.of(new ExtractedBatchResults.Status(400, "Bad request", null));

        var response = new BatchResponse<>(BATCH_NAME, BATCH_STATE_SUCCEEDED, responses, errors);

        assertThat(response.responses()).containsExactly("response1", "response2");
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().get(0).code()).isEqualTo(400);
        assertThat(response.errors().get(0).message()).isEqualTo("Bad request");
    }
}
