package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatExchange;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ToolAwareRepromptExecutorTest {

    static class PendingExecutor implements ChatExecutor {

        final CompletableFuture<ChatExchange> modelCall = new CompletableFuture<>();

        @Override
        public ChatResponse execute() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatResponse execute(List<ChatMessage> chatMessages) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<ChatExchange> exchangeAsync(List<ChatMessage> chatMessages) {
            return modelCall;
        }
    }

    @Test
    void cancelling_exchangeAsync_should_cancel_the_model_call() {

        PendingExecutor raw = new PendingExecutor();
        ChatExecutor executor = ToolAwareRepromptExecutor.wrapAsync(raw, null, null, null, null, null, null);

        executor.exchangeAsync(List.of(UserMessage.from("Hi"))).cancel(true);

        assertThat(raw.modelCall).isCancelled();
    }

    @Test
    void cancelling_executeAsync_should_cancel_the_model_call() {

        PendingExecutor raw = new PendingExecutor();
        ChatExecutor executor = ToolAwareRepromptExecutor.wrapAsync(raw, null, null, null, null, null, null);

        executor.executeAsync(List.of(UserMessage.from("Hi"))).cancel(true);

        assertThat(raw.modelCall).isCancelled();
    }
}
