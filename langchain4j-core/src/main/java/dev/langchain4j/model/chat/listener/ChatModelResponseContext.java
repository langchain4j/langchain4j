package dev.langchain4j.model.chat.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.Map;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * The response context. It contains {@link ChatResponse}, corresponding {@link ChatRequest} and attributes.
 * The attributes can be used to pass data between methods of a {@link ChatModelListener}
 * or between multiple {@link ChatModelListener}s.
 */
@Experimental
public class ChatModelResponseContext {

    private final ChatResponse chatResponse;
    @Deprecated(forRemoval = true)
    private final ChatModelResponse response;
    private final ChatRequest chatRequest;
    @Deprecated(forRemoval = true)
    private final ChatModelRequest request;
    private final String observabilityName;
    private final Map<Object, Object> attributes;

    public ChatModelResponseContext(ChatResponse chatResponse,
                                    ChatRequest chatRequest,
                                    String observabilityName,
                                    Map<Object, Object> attributes) {
        this.chatResponse = ensureNotNull(chatResponse, "chatResponse");
        this.response = ChatModelResponse.fromChatResponse(chatResponse);
        this.chatRequest = ensureNotNull(chatRequest, "chatRequest");
        this.request = ChatModelRequest.fromChatRequest(chatRequest);
        this.observabilityName = ensureNotBlank(observabilityName, "observabilityName");
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    /**
     * @deprecated please use {@link #ChatModelResponseContext(ChatResponse, ChatRequest, String, Map)} instead
     */
    @Deprecated(forRemoval = true)
    public ChatModelResponseContext(ChatModelResponse response,
                                    ChatModelRequest request,
                                    Map<Object, Object> attributes) {
        this.chatResponse = ChatModelResponse.toChatResponse(response);
        this.response = ensureNotNull(response, "response");
        this.chatRequest = ChatModelRequest.toChatRequest(request);
        this.request = ensureNotNull(request, "request");
        this.observabilityName = null;
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    /**
     * @deprecated please use {@link #ChatModelResponseContext(ChatResponse, ChatRequest, String, Map)} instead
     */
    @Deprecated(forRemoval = true)
    public ChatModelResponseContext(ChatModelResponse response,
                                    ChatModelRequest request,
                                    String observabilityName,
                                    Map<Object, Object> attributes) {
        this.chatResponse = ChatModelResponse.toChatResponse(response);
        this.response = ensureNotNull(response, "response");
        this.chatRequest = ChatModelRequest.toChatRequest(request);
        this.request = ensureNotNull(request, "request");
        this.observabilityName = ensureNotBlank(observabilityName, "observabilityName");
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    public ChatResponse chatResponse() {
        return chatResponse;
    }

    /**
     * @deprecated please use {@link #chatResponse()} instead
     */
    @Deprecated(forRemoval = true)
    public ChatModelResponse response() {
        return response;
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
     * TODO
     *
     * @return
     */
    public String observabilityName() {
        return observabilityName;
    }

    /**
     * @return The attributes map. It can be used to pass data between methods of a {@link ChatModelListener}
     * or between multiple {@link ChatModelListener}s.
     */
    public Map<Object, Object> attributes() {
        return attributes;
    }
}
