package dev.langchain4j.experimental.durable.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskPausedExceptionTest {

    @Test
    void should_create_with_reason_and_pending_key() {
        TaskPausedException ex = new TaskPausedException("Need approval", "approvalKey");

        assertThat(ex.reason()).isEqualTo("Need approval");
        assertThat(ex.pendingOutputKey()).isEqualTo("approvalKey");
        assertThat(ex.getMessage()).contains("Need approval");
    }

    @Test
    void should_create_with_reason_only() {
        TaskPausedException ex = new TaskPausedException("Waiting for data");

        assertThat(ex.reason()).isEqualTo("Waiting for data");
        assertThat(ex.pendingOutputKey()).isNull();
    }

    @Test
    void should_include_reason_in_message() {
        TaskPausedException ex = new TaskPausedException("External callback pending", "callbackResult");

        assertThat(ex.getMessage()).isEqualTo("Task paused: External callback pending");
    }

    @Test
    void should_be_instance_of_lang_chain4j_exception() {
        TaskPausedException ex = new TaskPausedException("paused");

        assertThat(ex).isInstanceOf(dev.langchain4j.exception.LangChain4jException.class);
    }

    @Test
    void should_have_null_pending_key_when_single_arg_constructor_used() {
        TaskPausedException ex = new TaskPausedException("batch job paused");

        assertThat(ex.pendingOutputKey()).isNull();
        assertThat(ex.reason()).isEqualTo("batch job paused");
    }

    @Test
    void not_retryable_by_default_retry_policy() {
        TaskPausedException ex = new TaskPausedException("paused");
        RetryPolicy policy = RetryPolicy.builder().build();

        assertThat(policy.isRetryable(ex)).isFalse();
    }
}
