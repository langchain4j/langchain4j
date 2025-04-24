package dev.langchain4j.model.chat.listener;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.request.ChatRequest;

import java.util.Map;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * The chat model error context.
 * It contains the error, corresponding {@link ChatRequest}, {@link ModelProvider} and attributes.
 * The attributes can be used to pass data between methods of a {@link ChatModelListener}
 * or between multiple {@link ChatModelListener}s.
 */
public class ChatModelErrorContext {

    private final Throwable error;
    private final ChatRequest chatRequest;
    private final ModelProvider modelProvider;
    private final Map<Object, Object> attributes;

    public ChatModelErrorContext(Throwable error,
                                 ChatRequest chatRequest,
                                 ModelProvider modelProvider,
                                 Map<Object, Object> attributes) {
        this.error = ensureNotNull(error, "error");
        this.chatRequest = ensureNotNull(chatRequest, "chatRequest");
        this.modelProvider = modelProvider;
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    /**
     * @return The error that occurred.
     */
    public Throwable error() {
        return error;
    }

    public ChatRequest chatRequest() {
        return chatRequest;
    }

    public ModelProvider modelProvider() {
        return modelProvider;
    }

    /**
     * @return The attributes map. It can be used to pass data between methods of a {@link ChatModelListener}
     * or between multiple {@link ChatModelListener}s.
     */
    public Map<Object, Object> attributes() {
        return attributes;
    }
}
