package dev.langchain4j.agentic.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.service.TokenStream;
import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class DelayedResponseTest {

    @Test
    void asyncResponse_rethrows_original_runtime_exception_not_completionException() {
        AgentInvocationException original = new AgentInvocationException("boom");
        AsyncResponse<String> response = new AsyncResponse<>(() -> {
            throw original;
        });

        assertThatThrownBy(response::blockingGet)
                .isInstanceOf(AgentInvocationException.class)
                .isSameAs(original);
    }

    @Test
    void pendingResponse_rethrows_original_runtime_exception_not_completionException() {
        AgentInvocationException original = new AgentInvocationException("boom");
        PendingResponse<String> response = new PendingResponse<>("id");
        response.completeExceptionally(original);

        assertThatThrownBy(response::blockingGet)
                .isInstanceOf(AgentInvocationException.class)
                .isSameAs(original);
    }

    @Test
    void streamingResponse_rethrows_original_runtime_exception_not_completionException() {
        AgentInvocationException original = new AgentInvocationException("boom");
        AtomicReference<Consumer<Throwable>> errorHandler = new AtomicReference<>();

        TokenStream tokenStream = mock(TokenStream.class, RETURNS_SELF);
        doAnswer(invocation -> {
                    errorHandler.set(invocation.getArgument(0));
                    return tokenStream;
                })
                .when(tokenStream)
                .onError(any());
        doAnswer(invocation -> {
                    errorHandler.get().accept(original);
                    return null;
                })
                .when(tokenStream)
                .start();

        StreamingResponse response = new StreamingResponse(tokenStream);

        assertThatThrownBy(response::blockingGet)
                .isInstanceOf(AgentInvocationException.class)
                .isSameAs(original);
    }

    @Test
    void checked_exception_cause_is_left_wrapped_in_completionException() {
        IOException checked = new IOException("io");
        PendingResponse<String> response = new PendingResponse<>("id");
        response.completeExceptionally(checked);

        assertThatThrownBy(response::blockingGet)
                .isInstanceOf(CompletionException.class)
                .hasCause(checked);
    }

    @Test
    void successful_response_is_returned_unchanged() {
        PendingResponse<String> response = new PendingResponse<>("id");
        response.complete("ok");

        assertThat(response.blockingGet()).isEqualTo("ok");
    }

    @Test
    void timeout_overload_rethrows_original_runtime_exception_not_executionException() {
        AgentInvocationException original = new AgentInvocationException("boom");
        PendingResponse<String> response = new PendingResponse<>("id");
        response.completeExceptionally(original);

        assertThatThrownBy(() -> response.blockingGet(1, TimeUnit.SECONDS))
                .isInstanceOf(AgentInvocationException.class)
                .isSameAs(original);
    }

    @Test
    void timeout_overload_leaves_checked_cause_wrapped() {
        IOException checked = new IOException("io");
        PendingResponse<String> response = new PendingResponse<>("id");
        response.completeExceptionally(checked);

        assertThatThrownBy(() -> response.blockingGet(1, TimeUnit.SECONDS))
                .isInstanceOf(RuntimeException.class)
                .hasCause(checked);
    }

    @Test
    void timeout_overload_still_times_out_when_the_response_is_not_completed() {
        PendingResponse<String> response = new PendingResponse<>("id");

        assertThatThrownBy(() -> response.blockingGet(1, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
    }

    @Test
    void timeout_overload_returns_successful_response_unchanged() throws Exception {
        PendingResponse<String> response = new PendingResponse<>("id");
        response.complete("ok");

        assertThat(response.blockingGet(1, TimeUnit.SECONDS)).isEqualTo("ok");
    }
}
