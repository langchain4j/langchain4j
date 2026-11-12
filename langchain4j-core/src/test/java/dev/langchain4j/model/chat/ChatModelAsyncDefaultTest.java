package dev.langchain4j.model.chat;

import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

    @Test
    void chatAsync_with_empty_list_of_messages_returns_a_failed_future() {
        ChatModel model = ChatModelMock.thatAlwaysResponds("Berlin");

        assertThat(model.chatAsync(List.of()))
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chatAsync_without_messages_returns_a_failed_future() {
        ChatModel model = ChatModelMock.thatAlwaysResponds("Berlin");

        assertThat(model.chatAsync(new ChatMessage[0]))
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chatAsync_with_null_user_message_returns_a_failed_future() {
        ChatModel model = ChatModelMock.thatAlwaysResponds("Berlin");

        assertThat(model.chatAsync((String) null))
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chatAsync_with_null_chat_request_returns_a_failed_future() {
        ChatModel model = ChatModelMock.thatAlwaysResponds("Berlin");

        assertThat(model.chatAsync((ChatRequest) null))
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(NullPointerException.class);
    }

    @Test
    void chatAsync_returns_a_failed_future_without_notifying_listeners_when_the_request_cannot_be_prepared() {
        ChatModelListener listener = mock(ChatModelListener.class);
        ChatModelMock model = spy(ChatModelMock.thatAlwaysResponds("Berlin").withListeners(listener));
        IllegalStateException error = new IllegalStateException("cannot merge default parameters");
        doThrow(error).when(model).defaultRequestParameters();

        CompletableFuture<ChatResponse> future =
                model.chatAsync(ChatRequest.builder().messages(UserMessage.from("hi")).build());

        assertThat(future)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isSameAs(error);
        verifyNoInteractions(listener);
    }

    @Test
    void chatAsync_returns_a_failed_future_and_notifies_listeners_once_when_doChatAsync_returns_null() {
        ChatModelListener listener = mock(ChatModelListener.class);
        ChatModelMock model = spy(ChatModelMock.thatAlwaysResponds("Berlin").withListeners(listener));
        doReturn(null).when(model).doChatAsync(any());

        CompletableFuture<ChatResponse> future =
                model.chatAsync(ChatRequest.builder().messages(UserMessage.from("hi")).build());

        assertThat(future)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(IllegalStateException.class)
                .withMessageContaining("doChatAsync(ChatRequest) returned null");
        verify(listener).onRequest(any());
        verify(listener).onError(any());
        verify(listener, never()).onResponse(any());
    }
}
