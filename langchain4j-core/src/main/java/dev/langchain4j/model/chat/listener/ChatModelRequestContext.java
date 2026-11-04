package dev.langchain4j.model.chat.listener;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.Map;

/**
 * The chat model request context.
 * It contains the {@link ChatRequest}, {@link ModelProvider} and attributes.
 * The attributes can be used to pass data between methods of a {@link ChatModelListener}
 * or between multiple {@link ChatModelListener}s.
 */
public class ChatModelRequestContext {

    private final ChatRequest chatRequest;
    private final ModelProvider modelProvider;
    private final Map<Object, Object> attributes;
    private final boolean streaming;

    /**
     * Creates a request context that reports {@code false} from {@link #isStreaming()}.
     */
    public ChatModelRequestContext(
            ChatRequest chatRequest, ModelProvider modelProvider, Map<Object, Object> attributes) {
        this(chatRequest, modelProvider, attributes, false);
    }

    /**
     * @param chatRequest   the request that was sent to the model.
     * @param modelProvider the provider of the model.
     * @param attributes    the attributes that can be used to pass data between methods of a
     *                      {@link ChatModelListener} or between multiple {@link ChatModelListener}s.
     * @param streaming     whether the call was initiated through a
     *                      {@link dev.langchain4j.model.chat.StreamingChatModel}.
     * @since 1.23.0
     */
    public ChatModelRequestContext(
            ChatRequest chatRequest, ModelProvider modelProvider, Map<Object, Object> attributes, boolean streaming) {
        this.chatRequest = ensureNotNull(chatRequest, "chatRequest");
        this.modelProvider = modelProvider;
        this.attributes = ensureNotNull(attributes, "attributes");
        this.streaming = streaming;
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

    /**
     * Tells whether the call that produced this context was initiated through a
     * {@link dev.langchain4j.model.chat.StreamingChatModel} rather than through a
     * {@link dev.langchain4j.model.chat.ChatModel}. Both streaming entry points report {@code true}:
     * the handler-based {@code chat(request, handler)} API and the reactive {@code chat(request)} API.
     * It allows a {@link ChatModelListener} to report streaming calls differently, for example by
     * setting a {@code stream} attribute on a span or by recording a different metric.
     *
     * @return {@code true} if the call was initiated through a
     * {@link dev.langchain4j.model.chat.StreamingChatModel}, {@code false} otherwise.
     * @since 1.23.0
     */
    public boolean isStreaming() {
        return streaming;
    }
}
