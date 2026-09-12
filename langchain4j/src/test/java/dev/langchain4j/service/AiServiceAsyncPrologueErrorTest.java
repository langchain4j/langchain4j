package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.chat.mock.ChatModelMock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a failure in the invocation <em>prologue</em> (system/user message prep, prompt-template rendering)
 * is delivered through the returned future/publisher for async and reactive return types, rather than being thrown
 * synchronously at the call site - which would be invisible to a purely-async caller that only attaches
 * {@code .exceptionally()} / {@code onError}.
 */
class AiServiceAsyncPrologueErrorTest {

    interface AsyncAssistant {
        // The template references {{name}}, but there is no matching @V parameter, so rendering the user message
        // fails in the prologue.
        @UserMessage("Hello {{name}}")
        CompletableFuture<String> chat();
    }

    interface ReactiveAssistant {
        @UserMessage("Hello {{name}}")
        Flow.Publisher<String> chat();
    }

    @Test
    void async_prologue_failure_is_delivered_via_the_future_not_thrown() throws Exception {
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("hi"))
                .build();

        // Must NOT throw synchronously.
        CompletableFuture<String> future = assistantChatWithoutSyncThrow(assistant);

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(() -> future.get(5, SECONDS)).hasMessageContaining("name");
    }

    @Test
    void reactive_prologue_failure_is_delivered_via_onError_not_thrown() throws Exception {
        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("hi"))
                .build();

        // Must NOT throw synchronously.
        Flow.Publisher<String> publisher = assistantChatWithoutSyncThrow(assistant);

        CompletableFuture<Throwable> onError = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {}

            @Override
            public void onError(Throwable throwable) {
                onError.complete(throwable);
            }

            @Override
            public void onComplete() {
                onError.complete(null);
            }
        });

        assertThat(onError.get(5, SECONDS)).isNotNull().hasMessageContaining("name");
    }

    private static CompletableFuture<String> assistantChatWithoutSyncThrow(AsyncAssistant assistant) {
        java.util.concurrent.atomic.AtomicReference<CompletableFuture<String>> ref =
                new java.util.concurrent.atomic.AtomicReference<>();
        assertThatCode(() -> ref.set(assistant.chat())).doesNotThrowAnyException();
        return ref.get();
    }

    private static Flow.Publisher<String> assistantChatWithoutSyncThrow(ReactiveAssistant assistant) {
        java.util.concurrent.atomic.AtomicReference<Flow.Publisher<String>> ref =
                new java.util.concurrent.atomic.AtomicReference<>();
        assertThatCode(() -> ref.set(assistant.chat())).doesNotThrowAnyException();
        return ref.get();
    }
}
