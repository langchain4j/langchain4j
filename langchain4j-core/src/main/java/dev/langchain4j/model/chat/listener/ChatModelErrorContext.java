package dev.langchain4j.model.chat.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.request.ChatRequest;

import java.util.Map;
import java.util.UUID;

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
    private final UUID invocationId;

    /**
     * @deprecated use {@link #builder()} instead
     */
    @Deprecated(since = "1.24.0")
    public ChatModelErrorContext(Throwable error,
                                 ChatRequest chatRequest,
                                 ModelProvider modelProvider,
                                 Map<Object, Object> attributes) {
        this(builder()
                .error(error)
                .chatRequest(chatRequest)
                .modelProvider(modelProvider)
                .attributes(attributes));
    }
    private ChatModelErrorContext(Builder builder) {
        this.error = ensureNotNull(builder.error, "error");
        this.chatRequest = ensureNotNull(builder.chatRequest, "chatRequest");
        this.modelProvider = builder.modelProvider;
        this.attributes = ensureNotNull(builder.attributes, "attributes");
        this.invocationId = builder.invocationId;
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
     * @return the id of the AI Service invocation this call belongs to: the one passed with
     * {@link ChatRequestOptions.Builder#invocationId(UUID)} if any, otherwise the one whose work (a tool, a guardrail,
     * retrieval, ...) makes the call, otherwise {@code null}. It is the {@link InvocationContext#invocationId()} of
     * the AI Service invocation, not an id of this model call.
     * @since 1.24.0
     */
    @Experimental
    public UUID invocationId() {
        return invocationId;
    }

    /**
     * @since 1.24.0
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @since 1.24.0
     */
    public static final class Builder {

        private Throwable error;
        private ChatRequest chatRequest;
        private ModelProvider modelProvider;
        private Map<Object, Object> attributes;
        private UUID invocationId;

        Builder() {}

        /**
         * Required: {@link #build()} fails if it is not set.
         */
        public Builder error(Throwable error) {
            this.error = error;
            return this;
        }

        /**
         * Required: {@link #build()} fails if it is not set.
         */
        public Builder chatRequest(ChatRequest chatRequest) {
            this.chatRequest = chatRequest;
            return this;
        }

        public Builder modelProvider(ModelProvider modelProvider) {
            this.modelProvider = modelProvider;
            return this;
        }

        /**
         * Required: {@link #build()} fails if it is not set.
         */
        public Builder attributes(Map<Object, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        @Experimental
        public Builder invocationId(UUID invocationId) {
            this.invocationId = invocationId;
            return this;
        }

        public ChatModelErrorContext build() {
            return new ChatModelErrorContext(this);
        }
    }
}
