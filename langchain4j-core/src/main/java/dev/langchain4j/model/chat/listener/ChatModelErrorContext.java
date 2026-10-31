package dev.langchain4j.model.chat.listener;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.Map;

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
    private final boolean streaming;

    /**
     * Creates an error context that reports {@code false} from {@link #isStreaming()}.
     */
    public ChatModelErrorContext(
            Throwable error, ChatRequest chatRequest, ModelProvider modelProvider, Map<Object, Object> attributes) {
        this(error, chatRequest, modelProvider, attributes, false);
    }

    /**
     * @param error         the error that occurred.
     * @param chatRequest   the request that failed.
     * @param modelProvider the provider of the model.
     * @param attributes    the attributes that can be used to pass data between methods of a
     *                      {@link ChatModelListener} or between multiple {@link ChatModelListener}s.
     * @param streaming     whether the call was initiated through a
     *                      {@link dev.langchain4j.model.chat.StreamingChatModel}.
     * @since 1.23.0
     */
    public ChatModelErrorContext(
            Throwable error,
            ChatRequest chatRequest,
            ModelProvider modelProvider,
            Map<Object, Object> attributes,
            boolean streaming) {
        this.error = ensureNotNull(error, "error");
        this.chatRequest = ensureNotNull(chatRequest, "chatRequest");
        this.modelProvider = modelProvider;
        this.attributes = ensureNotNull(attributes, "attributes");
        this.streaming = streaming;
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

    /**
     * Tells whether the failed call was initiated through a
     * {@link dev.langchain4j.model.chat.StreamingChatModel} rather than through a
     * {@link dev.langchain4j.model.chat.ChatModel}.
     *
     * @return {@code true} if the call was initiated through a
     * {@link dev.langchain4j.model.chat.StreamingChatModel}, {@code false} otherwise.
     * @since 1.23.0
     * @see ChatModelRequestContext#isStreaming()
     */
    public boolean isStreaming() {
        return streaming;
    }
}
