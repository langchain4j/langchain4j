package dev.langchain4j.model.chat;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Adapts a {@link StreamingChatModel} to the {@link ChatModel} interface
 * by collecting the streaming response synchronously.
 */
public class StreamingChatModelAdapter implements ChatModel {

    private final StreamingChatModel streamingChatModel;

    private StreamingChatModelAdapter(StreamingChatModel streamingChatModel) {
        this.streamingChatModel = ensureNotNull(streamingChatModel, "streamingChatModel");
    }

    public static ChatModel adapt(StreamingChatModel streamingChatModel) {
        return new StreamingChatModelAdapter(streamingChatModel);
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        streamingChatModel.chat(chatRequest, handler);
        try {
            return handler.get();
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ExecutionException executionException
                    && executionException.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return streamingChatModel.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return streamingChatModel.listeners();
    }

    @Override
    public ModelProvider provider() {
        return streamingChatModel.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return streamingChatModel.supportedCapabilities();
    }
}
