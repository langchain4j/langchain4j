package dev.langchain4j.model.chat.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.request.ChatRequest;

import java.util.Map;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * The chat model request context.
 * It contains the {@link ChatRequest} and attributes.
 * The attributes can be used to pass data between methods of a {@link ChatModelListener}
 * or between multiple {@link ChatModelListener}s.
 */
@Experimental
public class ChatModelRequestContext {

    private final ChatRequest chatRequest;
    @Deprecated(forRemoval = true)
    private final ChatModelRequest request;
    private final ModelProvider modelProvider;
    private final Map<Object, Object> attributes;

    public ChatModelRequestContext(ChatRequest chatRequest,
                                   ModelProvider modelProvider,
                                   Map<Object, Object> attributes) {
        this.chatRequest = ensureNotNull(chatRequest, "chatRequest");
        this.request = ChatModelRequest.fromChatRequest(chatRequest);
        this.modelProvider = modelProvider;
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    /**
     * @deprecated please use {@link #ChatModelRequestContext(ChatRequest, ModelProvider, Map)} instead
     */
    @Deprecated(forRemoval = true)
    public ChatModelRequestContext(ChatModelRequest request, Map<Object, Object> attributes) {
        this.chatRequest = ChatModelRequest.toChatRequest(request);
        this.request = ensureNotNull(request, "request");
        this.modelProvider = null;
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    /**
     * @deprecated please use {@link #ChatModelRequestContext(ChatRequest, ModelProvider, Map)} instead
     */
    @Deprecated(forRemoval = true)
    public ChatModelRequestContext(ChatModelRequest request,
                                   ModelProvider modelProvider,
                                   Map<Object, Object> attributes) {
        this.chatRequest = ChatModelRequest.toChatRequest(request);
        this.request = ensureNotNull(request, "request");
        this.modelProvider = modelProvider;
        this.attributes = ensureNotNull(attributes, "attributes");
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
