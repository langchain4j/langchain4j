package dev.langchain4j.model.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreamingChatModelListenerTest {

    static class TestStreamingChatModel implements StreamingChatModel {

        private final List<ChatModelListener> listeners;

        TestStreamingChatModel(List<ChatModelListener> listeners) {
            this.listeners = listeners;
        }

        @Override
        public List<ChatModelListener> listeners() {
            return listeners;
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            List<ChatMessage> messages = chatRequest.messages();
            UserMessage lastMessage = (UserMessage) messages.get(messages.size() - 1);
            ChatResponse chatResponse = ChatResponse.builder()
                    .aiMessage(AiMessage.from(lastMessage.singleText()))
                    .build();
            handler.onCompleteResponse(chatResponse);
        }
    }

    static class NoOpHandler implements StreamingChatResponseHandler {

        @Override
        public void onPartialResponse(String partialResponse) {}

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {}

        @Override
        public void onError(Throwable error) {}
    }

    @Test
    void should_propagate_options_attributes_to_streaming_listener() {

        // given
        ChatModelListener listener = spy(new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                assertThat(requestContext.attributes()).containsEntry("tenantId", "acme-co");
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                assertThat(responseContext.attributes()).containsEntry("tenantId", "acme-co");
            }
        });
        TestStreamingChatModel model = new TestStreamingChatModel(List.of(listener));

        ChatRequestOptions options = ChatRequestOptions.builder()
                .listenerAttribute("tenantId", "acme-co")
                .build();

        // when
        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from("hi")).build();
        model.chat(chatRequest, new NoOpHandler(), options);

        // then
        verify(listener).onRequest(any());
        verify(listener).onResponse(any());
    }

    @Test
    void should_propagate_options_attributes_to_streaming_listener_on_error() {

        // given
        ChatModelListener listener = spy(new ChatModelListener() {
            @Override
            public void onError(ChatModelErrorContext errorContext) {
                assertThat(errorContext.attributes()).containsEntry("userId", "user-42");
            }
        });
        TestStreamingChatModel model = new TestStreamingChatModel(List.of(listener)) {
            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                handler.onError(new RuntimeException("streaming failed"));
            }
        };

        ChatRequestOptions options = ChatRequestOptions.builder()
                .listenerAttribute("userId", "user-42")
                .build();

        // when
        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from("hi")).build();
        model.chat(chatRequest, new NoOpHandler(), options);

        // then
        verify(listener).onError(any());
    }

    @Test
    void should_handle_null_options_in_streaming() {

        // given
        ChatModelListener listener = spy(new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                assertThat(requestContext.attributes()).isEmpty();
            }
        });
        TestStreamingChatModel model = new TestStreamingChatModel(List.of(listener));

        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from("hi")).build();

        // when
        model.chat(chatRequest, new NoOpHandler(), null);

        // then
        verify(listener).onRequest(any());
        verify(listener).onResponse(any());
    }

    @Test
    void should_allow_listeners_to_mutate_attributes_from_options_in_streaming() {

        // given
        ChatModelListener listener = spy(new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                assertThat(requestContext.attributes()).containsEntry("key", "value");
                requestContext.attributes().put("extra", "data");
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                assertThat(responseContext.attributes()).containsEntry("extra", "data");
            }
        });
        TestStreamingChatModel model = new TestStreamingChatModel(List.of(listener));

        ChatRequestOptions options =
                ChatRequestOptions.builder().listenerAttribute("key", "value").build();

        // when
        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from("hi")).build();
        model.chat(chatRequest, new NoOpHandler(), options);

        // then
        verify(listener).onRequest(any());
        verify(listener).onResponse(any());
    }
}
