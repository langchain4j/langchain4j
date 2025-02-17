package dev.langchain4j.model.chat.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.Map;

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
    private final ModelProvider modelProvider;
    private final Map<Object, Object> attributes;

    public ChatModelResponseContext(ChatResponse chatResponse,
                                    ChatRequest chatRequest,
                                    ModelProvider modelProvider,
                                    Map<Object, Object> attributes) {
        this.chatResponse = ensureNotNull(chatResponse, "chatResponse");
        this.response = ChatModelResponse.fromChatResponse(chatResponse);
        this.chatRequest = ensureNotNull(chatRequest, "chatRequest");
        this.request = ChatModelRequest.fromChatRequest(chatRequest);
        this.modelProvider = modelProvider;
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    /**
     * @deprecated please use {@link #ChatModelResponseContext(ChatResponse, ChatRequest, ModelProvider, Map)} instead
     */
    @Deprecated(forRemoval = true)
    public ChatModelResponseContext(ChatModelResponse response,
                                    ChatModelRequest request,
                                    Map<Object, Object> attributes) {
        this.chatResponse = ChatModelResponse.toChatResponse(response);
        this.response = ensureNotNull(response, "response");
        this.chatRequest = ChatModelRequest.toChatRequest(request);
        this.request = ensureNotNull(request, "request");
        this.modelProvider = null;
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    /**
     * @deprecated please use {@link #ChatModelResponseContext(ChatResponse, ChatRequest, ModelProvider, Map)} instead
     */
    @Deprecated(forRemoval = true)
    public ChatModelResponseContext(ChatModelResponse response,
                                    ChatModelRequest request,
                                    ModelProvider modelProvider,
                                    Map<Object, Object> attributes) {
        this.chatResponse = ChatModelResponse.toChatResponse(response);
        this.response = ensureNotNull(response, "response");
        this.chatRequest = ChatModelRequest.toChatRequest(request);
        this.request = ensureNotNull(request, "request");
        this.modelProvider = modelProvider;
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
     * The name of the GenAI system (LLM provider).
     * Each {@link ChatLanguageModel} and {@link StreamingChatLanguageModel} implementation can return a predefined,
     * OpenTelemetry-compliant name that can be directly used as the OpenTelemetry "gen_ai.system" attribute.
     * <p>
     * Please note that this method can return {@code null} in the future.
     * <p>
     * See more details
     * <a href="https://opentelemetry.io/docs/specs/semconv/attributes-registry/gen-ai/#gen-ai-system">here</a>.
     */
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
