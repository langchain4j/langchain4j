package dev.langchain4j.model.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BatchItemResultTest {

    @Test
    void success_shouldExposeResponseAndNoError() {
        BatchItemResult<String> result = BatchItemResult.success("hello");

        assertThat(result).isInstanceOf(BatchItemResult.Success.class);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.response()).isEqualTo("hello");
        assertThat(result.error()).isNull();
    }

    @Test
    void failure_shouldExposeErrorAndNoResponse() {
        var error = new BatchError(500, "Internal error", null);
        BatchItemResult<String> result = BatchItemResult.failure(error);

        assertThat(result).isInstanceOf(BatchItemResult.Failure.class);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).isEqualTo(error);
        assertThat(result.response()).isNull();
    }

    @Test
    void success_shouldRejectNullResponse() {
        assertThatThrownBy(() -> BatchItemResult.success(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failure_shouldRejectNullError() {
        assertThatThrownBy(() -> BatchItemResult.failure(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
