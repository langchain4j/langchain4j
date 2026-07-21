package dev.langchain4j.model.chat;

import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatModelAsyncDefaultTest {

    /**
     * A model that implements only the blocking {@link ChatModel#doChat(ChatRequest)}, leaving
     * {@link ChatModel#doChatAsync(ChatRequest)} as the throwing default.
     */
    static class BlockingOnlyChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("Berlin"))
                    .metadata(ChatResponseMetadata.builder().build())
                    .build();
        }
    }

    @Test
    void chatAsync_of_a_blocking_only_model_fails_with_AsyncNotSupportedException() {
        ChatModel model = new BlockingOnlyChatModel();

        CompletableFuture<ChatResponse> future =
                model.chatAsync(ChatRequest.builder().messages(UserMessage.from("hi")).build());

        // The "not async" signal is an AsyncNotSupportedException (not an opaque RuntimeException), so the
        // asynchronous RAG orchestrators can recognize it and offload or fail loudly. It is delivered through the
        // future, not thrown synchronously.
        assertThatThrownBy(() -> future.get(5, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isExactlyInstanceOf(AsyncNotSupportedException.class)
                .hasMessageContaining("doChatAsync");
    }

    @Test
    void doChatAsync_default_returns_a_failed_future() {
        ChatModel model = new BlockingOnlyChatModel();

        CompletableFuture<ChatResponse> future =
                model.doChatAsync(ChatRequest.builder().messages(UserMessage.from("hi")).build());

        // The default signals "not async" through the returned future rather than throwing synchronously.
        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isExactlyInstanceOf(AsyncNotSupportedException.class)
                .hasMessageContaining("doChatAsync");
    }

    @Test
    void chat_of_a_blocking_only_model_still_works() {
        ChatModel model = new BlockingOnlyChatModel();

        ChatResponse response = model.chat(ChatRequest.builder().messages(UserMessage.from("hi")).build());

        assertThat(response.aiMessage().text()).isEqualTo("Berlin");
    }
}
