package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.data.MapEntry.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class ChatModelListenerTest {

    static class TestChatModel implements ChatModel {

        private final List<ChatModelListener> listeners;

        TestChatModel(List<ChatModelListener> listeners) {
            this.listeners = listeners;
        }

        @Override
        public List<ChatModelListener> listeners() {
            return listeners;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("hi"))
                    .build();
        }
    }

    static class SuccessfulListener implements ChatModelListener {

        @Override
        public void onRequest(ChatModelRequestContext requestContext) {
        }

        @Override
        public void onResponse(ChatModelResponseContext responseContext) {
        }

        @Override
        public void onError(ChatModelErrorContext errorContext) {
        }
    }

    static class FailingListener implements ChatModelListener {

        @Override
        public void onRequest(ChatModelRequestContext requestContext) {
            throw new RuntimeException("something went wrong in onRequest()");
        }

        @Override
        public void onResponse(ChatModelResponseContext responseContext) {
            throw new RuntimeException("something went wrong in onResponse()");
        }

        @Override
        public void onError(ChatModelErrorContext errorContext) {
            throw new RuntimeException("something went wrong in onError()");
        }
    }

    @Test
    void should_call_listeners_in_order_of_declaration() {

        // given
        ChatModelListener listener1 = spy(new SuccessfulListener());
        ChatModelListener listener2 = spy(new SuccessfulListener());
        TestChatModel model = new TestChatModel(List.of(listener1, listener2));

        // when
        model.chat("hi");

        // then
        InOrder inOrder = inOrder(listener1, listener2);
        inOrder.verify(listener1).onRequest(any());
        inOrder.verify(listener2).onRequest(any());
        inOrder.verify(listener1).onResponse(any());
        inOrder.verify(listener2).onResponse(any());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void should_ignore_exceptions_thrown_by_listeners() {

        // given
        ChatModelListener failingListener = spy(new FailingListener());
        ChatModelListener successfulListener = spy(new SuccessfulListener());
        TestChatModel model = new TestChatModel(List.of(failingListener, successfulListener));

        // when - then
        assertThatNoException().isThrownBy(() -> model.chat("hi"));

        verify(failingListener).onRequest(any());
        verify(failingListener).onResponse(any());
        verifyNoMoreInteractions(failingListener);

        verify(successfulListener).onRequest(any());
        verify(successfulListener).onResponse(any());
        verifyNoMoreInteractions(successfulListener);
    }

    @Test
    void should_pass_attributes_from_one_listener_to_another() {

        // given
        ChatModelListener listener1 = spy(new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                requestContext.attributes().put("my-attribute", "my-value");
            }
        });
        ChatModelListener listener2 = spy(new ChatModelListener() {
            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                assertThat(responseContext.attributes()).containsExactly(entry("my-attribute", "my-value"));
            }
        });
        TestChatModel model = new TestChatModel(List.of(listener1, listener2));

        // when
        model.chat("hi");

        // then
        verify(listener2).onResponse(any());
    }
}
