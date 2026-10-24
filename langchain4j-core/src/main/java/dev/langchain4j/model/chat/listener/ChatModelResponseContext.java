package dev.langchain4j.model.chat.listener;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.Map;

/**
 * The chat response context.
 * It contains {@link ChatResponse}, corresponding {@link ChatRequest}, {@link ModelProvider} and attributes.
 * The attributes can be used to pass data between methods of a {@link ChatModelListener}
 * or between multiple {@link ChatModelListener}s.
 */
public class ChatModelResponseContext {

    private final ChatResponse chatResponse;
    private final ChatRequest chatRequest;
    private final ModelProvider modelProvider;
    private final Map<Object, Object> attributes;
    private final boolean streaming;

    /**
     * Creates a response context that reports {@code false} from {@link #isStreaming()}.
     */
    public ChatModelResponseContext(
            ChatResponse chatResponse,
            ChatRequest chatRequest,
            ModelProvider modelProvider,
            Map<Object, Object> attributes) {
        this(chatResponse, chatRequest, modelProvider, attributes, false);
    }

    /**
     * @param chatResponse  the response returned by the model.
     * @param chatRequest   the request that produced the response.
     * @param modelProvider the provider of the model.
     * @param attributes    the attributes that can be used to pass data between methods of a
     *                      {@link ChatModelListener} or between multiple {@link ChatModelListener}s.
     * @param streaming     whether the call was initiated through a
     *                      {@link dev.langchain4j.model.chat.StreamingChatModel}.
     * @since 1.23.0
     */
    public ChatModelResponseContext(
            ChatResponse chatResponse,
            ChatRequest chatRequest,
            ModelProvider modelProvider,
            Map<Object, Object> attributes,
            boolean streaming) {
        this.chatResponse = ensureNotNull(chatResponse, "chatResponse");
        this.chatRequest = ensureNotNull(chatRequest, "chatRequest");
        this.modelProvider = modelProvider;
        this.attributes = ensureNotNull(attributes, "attributes");
        this.streaming = streaming;
    }

    public ChatResponse chatResponse() {
        return chatResponse;
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
     * Tells whether the call that produced this response was initiated through a
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
