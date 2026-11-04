package dev.langchain4j.model.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link ChatModelListener} is told whether the call it observes was initiated through a
 * {@link ChatModel} or through a {@link StreamingChatModel}, so that listeners can report it (e.g. as a
 * {@code stream} attribute in telemetry).
 *
 * @see ChatModelRequestContext#isStreaming()
 * @see ChatModelResponseContext#isStreaming()
 * @see ChatModelErrorContext#isStreaming()
 */
class ChatModelListenerStreamingFlagTest {

    private static final ChatRequest CHAT_REQUEST =
            ChatRequest.builder().messages(UserMessage.from("hi")).build();

    private static final ChatResponse CHAT_RESPONSE =
            ChatResponse.builder().aiMessage(AiMessage.from("hi")).build();

    @Test
    void should_report_a_non_streaming_call_on_request_and_response_when_ChatModel_chat_is_used() {

        // given
        RecordingListener listener = new RecordingListener();
        ChatModel model = new TestChatModel(List.of(listener), null);

        // when
        ChatResponse chatResponse = model.chat(CHAT_REQUEST);

        // then
        assertThat(chatResponse).isEqualTo(CHAT_RESPONSE);
        assertThat(listener.requestStreaming()).isFalse();
        assertThat(listener.responseStreaming()).isFalse();
        assertThat(listener.errorStreaming()).isNull();
    }

    @Test
    void should_report_a_non_streaming_call_when_ChatModel_chatAsync_is_used() {

        // given
        RecordingListener listener = new RecordingListener();
        ChatModel model = new TestChatModel(List.of(listener), null);

        // when
        CompletableFuture<ChatResponse> future = model.chatAsync(CHAT_REQUEST);

        // then
        assertThat(future).isCompletedWithValue(CHAT_RESPONSE);
        assertThat(listener.requestStreaming()).isFalse();
        assertThat(listener.responseStreaming()).isFalse();
        assertThat(listener.errorStreaming()).isNull();
    }

    @Test
    void should_report_a_non_streaming_call_on_error_when_ChatModel_fails() {

        // given
        RecordingListener listener = new RecordingListener();
        ChatModel model = new TestChatModel(List.of(listener), new RuntimeException("boom"));

        // when/then
        assertThatThrownBy(() -> model.chat(CHAT_REQUEST)).hasMessage("boom");
        assertThat(listener.requestStreaming()).isFalse();
        assertThat(listener.errorStreaming()).isFalse();
        assertThat(listener.responseStreaming()).isNull();
    }

    @Test
    void should_report_a_streaming_call_on_request_and_response_when_StreamingChatModel_handler_is_used() {

        // given
        RecordingListener listener = new RecordingListener();
        StreamingChatModel model = new TestStreamingChatModel(List.of(listener), null);

        // when
        model.chat(CHAT_REQUEST, new TestStreamingChatResponseHandler());

        // then
        assertThat(listener.requestStreaming()).isTrue();
        assertThat(listener.responseStreaming()).isTrue();
        assertThat(listener.errorStreaming()).isNull();
    }

    @Test
    void should_report_a_streaming_call_on_error_when_StreamingChatModel_handler_fails() {

        // given
        RecordingListener listener = new RecordingListener();
        StreamingChatModel model = new TestStreamingChatModel(List.of(listener), new RuntimeException("boom"));

        // when
        model.chat(CHAT_REQUEST, new TestStreamingChatResponseHandler());

        // then
        assertThat(listener.requestStreaming()).isTrue();
        assertThat(listener.errorStreaming()).isTrue();
        assertThat(listener.responseStreaming()).isNull();
    }

    @Test
    void should_report_a_streaming_call_on_request_and_response_when_StreamingChatModel_publisher_is_used() {

        // given
        RecordingListener listener = new RecordingListener();
        StreamingChatModel model = new TestStreamingChatModel(List.of(listener), null);

        // when
        ChatResponse chatResponse =
                StreamingChatModelHelper.chatAsync(model, CHAT_REQUEST).join();

        // then
        assertThat(chatResponse).isEqualTo(CHAT_RESPONSE);
        assertThat(listener.requestStreaming()).isTrue();
        assertThat(listener.responseStreaming()).isTrue();
        assertThat(listener.errorStreaming()).isNull();
    }

    @Test
    void should_report_a_streaming_call_on_error_when_StreamingChatModel_publisher_fails() {

        // given
        RecordingListener listener = new RecordingListener();
        StreamingChatModel model = new TestStreamingChatModel(List.of(listener), new RuntimeException("boom"));

        // when/then
        assertThatThrownBy(() ->
                        StreamingChatModelHelper.chatAsync(model, CHAT_REQUEST).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseExactlyInstanceOf(RuntimeException.class)
                .hasMessageContaining("boom");
        assertThat(listener.requestStreaming()).isTrue();
        assertThat(listener.errorStreaming()).isTrue();
        assertThat(listener.responseStreaming()).isNull();
    }

    @Test
    void should_report_a_non_streaming_call_when_contexts_are_created_with_the_legacy_constructors() {

        // when/then
        assertThat(new ChatModelRequestContext(CHAT_REQUEST, ModelProvider.OPEN_AI, new HashMap<>()).isStreaming())
                .isFalse();
        assertThat(new ChatModelResponseContext(CHAT_RESPONSE, CHAT_REQUEST, ModelProvider.OPEN_AI, new HashMap<>())
                        .isStreaming())
                .isFalse();
        assertThat(new ChatModelErrorContext(
                                new RuntimeException("boom"), CHAT_REQUEST, ModelProvider.OPEN_AI, new HashMap<>())
                        .isStreaming())
                .isFalse();
    }

    static class RecordingListener implements ChatModelListener {

        private final AtomicReference<Boolean> requestStreaming = new AtomicReference<>();
        private final AtomicReference<Boolean> responseStreaming = new AtomicReference<>();
        private final AtomicReference<Boolean> errorStreaming = new AtomicReference<>();

        @Override
        public void onRequest(ChatModelRequestContext requestContext) {
            requestStreaming.set(requestContext.isStreaming());
        }

        @Override
        public void onResponse(ChatModelResponseContext responseContext) {
            responseStreaming.set(responseContext.isStreaming());
        }

        @Override
        public void onError(ChatModelErrorContext errorContext) {
            errorStreaming.set(errorContext.isStreaming());
        }

        Boolean requestStreaming() {
            return requestStreaming.get();
        }

        Boolean responseStreaming() {
            return responseStreaming.get();
        }

        Boolean errorStreaming() {
            return errorStreaming.get();
        }
    }

    static class TestChatModel implements ChatModel {

        private final List<ChatModelListener> listeners;
        private final RuntimeException error;

        TestChatModel(List<ChatModelListener> listeners, RuntimeException error) {
            this.listeners = listeners;
            this.error = error;
        }

        @Override
        public List<ChatModelListener> listeners() {
            return listeners;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            if (error != null) {
                throw error;
            }
            return CHAT_RESPONSE;
        }

        @Override
        public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
            if (error != null) {
                return CompletableFuture.failedFuture(error);
            }
            return CompletableFuture.completedFuture(CHAT_RESPONSE);
        }
    }

    static class TestStreamingChatModel implements StreamingChatModel {

        private final List<ChatModelListener> listeners;
        private final RuntimeException error;

        TestStreamingChatModel(List<ChatModelListener> listeners, RuntimeException error) {
            this.listeners = listeners;
            this.error = error;
        }

        @Override
        public List<ChatModelListener> listeners() {
            return listeners;
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            if (error != null) {
                handler.onError(error);
            } else {
                handler.onCompleteResponse(CHAT_RESPONSE);
            }
        }

        @Override
        public Flow.Publisher<ChatModelStreamingEvent> doChat(ChatRequest chatRequest) {
            return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {

                private boolean terminated;

                @Override
                public void request(long n) {
                    if (terminated) {
                        return;
                    }
                    terminated = true;
                    if (error != null) {
                        subscriber.onError(error);
                    } else {
                        subscriber.onNext(new CompleteResponse(CHAT_RESPONSE));
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                    terminated = true;
                }
            });
        }
    }
}
