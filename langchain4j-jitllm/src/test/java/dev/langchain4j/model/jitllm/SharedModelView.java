package dev.langchain4j.model.jitllm;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;

/**
 * Wraps a model shared by all tests of a class in a view that is not {@link AutoCloseable}.
 * JUnit closes {@link AutoCloseable} arguments of parameterized tests after each invocation,
 * which would close the shared model after the first test. The test class closes the model itself.
 */
final class SharedModelView {

    private SharedModelView() {}

    static ChatModel of(ChatModel delegate) {
        return new ChatModel() {

            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                return delegate.doChat(chatRequest);
            }

            @Override
            public ChatRequestParameters defaultRequestParameters() {
                return delegate.defaultRequestParameters();
            }

            @Override
            public List<ChatModelListener> listeners() {
                return delegate.listeners();
            }
        };
    }

    static StreamingChatModel of(StreamingChatModel delegate) {
        return new StreamingChatModel() {

            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                delegate.doChat(chatRequest, handler);
            }

            @Override
            public ChatRequestParameters defaultRequestParameters() {
                return delegate.defaultRequestParameters();
            }

            @Override
            public List<ChatModelListener> listeners() {
                return delegate.listeners();
            }
        };
    }
}
