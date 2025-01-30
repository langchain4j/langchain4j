package dev.langchain4j.model.chat.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequest;

import java.util.Map;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * The error context. It contains the error, corresponding {@link ChatRequest} and attributes.
 * The attributes can be used to pass data between methods of a {@link ChatModelListener}
 * or between multiple {@link ChatModelListener}s.
 */
@Experimental
public class ChatModelErrorContext {

    private final Throwable error;
    private final ChatRequest chatRequest;
    @Deprecated(forRemoval = true)
    private final ChatModelRequest request;
    @Deprecated(forRemoval = true)
    private final ChatModelResponse partialResponse;
    private final Map<Object, Object> attributes;

    public ChatModelErrorContext(Throwable error,
                                 ChatRequest chatRequest,
                                 Map<Object, Object> attributes) {
        this.error = ensureNotNull(error, "error");
        this.chatRequest = ensureNotNull(chatRequest, "chatRequest");
        this.request = ChatModelRequest.fromChatRequest(chatRequest);
        this.partialResponse = null;
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    /**
     * @deprecated please use {@link #ChatModelErrorContext(Throwable, ChatRequest, Map)} instead
     */
    @Deprecated(forRemoval = true)
    public ChatModelErrorContext(Throwable error,
                                 ChatModelRequest request,
                                 ChatModelResponse partialResponse,
                                 Map<Object, Object> attributes) {
        this.error = ensureNotNull(error, "error");
        this.chatRequest = ChatModelRequest.toChatRequest(request);
        this.request = ensureNotNull(request, "request");
        this.partialResponse = partialResponse;
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

    /**
     * @deprecated please use {@link #chatRequest()} instead
     */
    @Deprecated(forRemoval = true)
    public ChatModelRequest request() {
        return request;
    }

    /**
     * @deprecated Partial response will not be available in future versions to simplify the design and implementation.
     * Please reach out if you have any concerns.
     */
    @Deprecated(forRemoval = true)
    public ChatModelResponse partialResponse() {
        return partialResponse;
    }

    /**
     * @return The attributes map. It can be used to pass data between methods of a {@link ChatModelListener}
     * or between multiple {@link ChatModelListener}s.
     */
    public Map<Object, Object> attributes() {
        return attributes;
    }
}
