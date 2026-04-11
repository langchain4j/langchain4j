package dev.langchain4j.model.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StreamingChatModelListenerTest {

    static class TestStreamingChatModel implements StreamingChatModel {

        private final List<ChatModelListener> listeners;
        private final boolean shouldFail;

        TestStreamingChatModel(List<ChatModelListener> listeners) {
            this(listeners, false);
        }

        TestStreamingChatModel(List<ChatModelListener> listeners, boolean shouldFail) {
            this.listeners = listeners;
            this.shouldFail = shouldFail;
        }

        @Override
        public List<ChatModelListener> listeners() {
            return listeners;
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            if (shouldFail) {
                handler.onError(new RuntimeException("streaming fail"));
            } else {
                handler.onCompleteResponse(
                        ChatResponse.builder().aiMessage(AiMessage.from("hi")).build());
            }
        }
    }

    @Test
    void should_propagate_options_attributes_to_listener_on_request_and_response() throws Exception {

        // given
        CompletableFuture<Boolean> verified = new CompletableFuture<>();
        ChatModelListener listener = spy(new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext ctx) {
                assertThat(ctx.attributes()).containsEntry("tenantId", "acme-co");
            }

            @Override
            public void onResponse(ChatModelResponseContext ctx) {
                assertThat(ctx.attributes()).containsEntry("tenantId", "acme-co");
                verified.complete(true);
            }
        });
        TestStreamingChatModel model = new TestStreamingChatModel(List.of(listener));
        ChatRequestOptions options = ChatRequestOptions.builder()
                .addListenerAttribute("tenantId", "acme-co")
                .build();

        // when
        model.chat(
                ChatRequest.builder().messages(UserMessage.from("hi")).build(), options, new StreamingChatResponseHandler() {
                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {}

                    @Override
                    public void onError(Throwable error) {}
                }
        );

        // then
        assertThat(verified.get(5, TimeUnit.SECONDS)).isTrue();
        verify(listener).onRequest(any());
        verify(listener).onResponse(any());
    }

    @Test
    void should_propagate_options_attributes_to_listener_on_error() throws Exception {

        // given
        CompletableFuture<Boolean> verified = new CompletableFuture<>();
        ChatModelListener listener = spy(new ChatModelListener() {
            @Override
            public void onError(ChatModelErrorContext ctx) {
                assertThat(ctx.attributes()).containsEntry("key", "value");
                verified.complete(true);
            }
        });
        TestStreamingChatModel model = new TestStreamingChatModel(List.of(listener), true);
        ChatRequestOptions options = ChatRequestOptions.builder()
                .addListenerAttribute("key", "value")
                .build();

        // when
        model.chat(
                ChatRequest.builder().messages(UserMessage.from("hi")).build(), options, new StreamingChatResponseHandler() {
                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {}

                    @Override
                    public void onError(Throwable error) {}
                }
        );

        // then
        assertThat(verified.get(5, TimeUnit.SECONDS)).isTrue();
        verify(listener).onError(any());
    }

    @Test
    void should_handle_null_options() {

        // given
        TestStreamingChatModel model = new TestStreamingChatModel(List.of());

        // when/then
        assertThatNoException()
                .isThrownBy(() -> model.chat(
                        ChatRequest.builder().messages(UserMessage.from("hi")).build(), null, new StreamingChatResponseHandler() {
                            @Override
                            public void onCompleteResponse(ChatResponse completeResponse) {}

                            @Override
                            public void onError(Throwable error) {}
                        }
                ));
    }
}
