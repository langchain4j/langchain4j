package dev.langchain4j.model.chat.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
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
    private final String system;
    private final Map<Object, Object> attributes;

    public ChatModelErrorContext(Throwable error,
                                 ChatRequest chatRequest,
                                 String system,
                                 Map<Object, Object> attributes) {
        this.error = ensureNotNull(error, "error");
        this.chatRequest = ensureNotNull(chatRequest, "chatRequest");
        this.request = ChatModelRequest.fromChatRequest(chatRequest);
        this.partialResponse = null;
        this.system = system;
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    /**
     * @deprecated please use {@link #ChatModelErrorContext(Throwable, ChatRequest, String, Map)} instead
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
        this.system = null;
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    /**
     * @deprecated please use {@link #ChatModelErrorContext(Throwable, ChatRequest, String, Map)} instead
     */
    @Deprecated(forRemoval = true)
    public ChatModelErrorContext(Throwable error,
                                 ChatModelRequest request,
                                 ChatModelResponse partialResponse,
                                 String system,
                                 Map<Object, Object> attributes) {
        this.error = ensureNotNull(error, "error");
        this.chatRequest = ChatModelRequest.toChatRequest(request);
        this.request = ensureNotNull(request, "request");
        this.partialResponse = partialResponse;
        this.system = system;
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
     * The name of the GenAI system (LLM provider). By default, each {@link ChatLanguageModel}
     * and {@link StreamingChatLanguageModel} implementation returns a predefined, OpenTelemetry-compliant name
     * that can be directly used as the OpenTelemetry "gen_ai.system" attribute.
     * See more details
     * <a href="https://opentelemetry.io/docs/specs/semconv/attributes-registry/gen-ai/#gen-ai-system">here</a>.
     */
    public String system() {
        return system;
    }

    /**
     * @return The attributes map. It can be used to pass data between methods of a {@link ChatModelListener}
     * or between multiple {@link ChatModelListener}s.
     */
    public Map<Object, Object> attributes() {
        return attributes;
    }
}
