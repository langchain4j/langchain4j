package dev.langchain4j.model.moderation.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.moderation.ModerationRequest;

import java.util.Map;
import java.util.UUID;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * The moderation model error context.
 * It contains the error, corresponding {@link ModerationRequest}, {@link ModelProvider} and attributes.
 * The attributes can be used to pass data between methods of a {@link ModerationModelListener}
 * or between multiple {@link ModerationModelListener}s.
 */
public class ModerationModelErrorContext {

    private final Throwable error;
    private final ModerationRequest moderationRequest;
    private final ModelProvider modelProvider;
    private final Map<Object, Object> attributes;
    private final UUID invocationId;

    /**
     * @deprecated use {@link #builder()} instead
     */
    @Deprecated(since = "1.24.0")
    public ModerationModelErrorContext(
            Throwable error,
            ModerationRequest moderationRequest,
            ModelProvider modelProvider,
            Map<Object, Object> attributes) {
        this(builder()
                .error(error)
                .moderationRequest(moderationRequest)
                .modelProvider(modelProvider)
                .attributes(attributes));
    }
    private ModerationModelErrorContext(Builder builder) {
        this.error = ensureNotNull(builder.error, "error");
        this.moderationRequest = ensureNotNull(builder.moderationRequest, "moderationRequest");
        this.modelProvider = ensureNotNull(builder.modelProvider, "modelProvider");
        this.attributes = ensureNotNull(builder.attributes, "attributes");
        this.invocationId = builder.invocationId;
    }

    /**
     * @return The error that occurred.
     */
    public Throwable error() {
        return error;
    }

    /**
     * @return The moderation request.
     */
    public ModerationRequest moderationRequest() {
        return moderationRequest;
    }

    /**
     * @return The model provider.
     */
    public ModelProvider modelProvider() {
        return modelProvider;
    }

    /**
     * @return The attributes map. It can be used to pass data between methods of a {@link ModerationModelListener}
     * or between multiple {@link ModerationModelListener}s.
     */
    public Map<Object, Object> attributes() {
        return attributes;
    }

    /**
     * @return the id of the AI Service invocation in which this call is made (the calls made by its tools, guardrails,
     * retrieval, moderation, chat memory, ...), otherwise {@code null}, for example when it is called outside of an AI
     * Service.
     * It is the {@link InvocationContext#invocationId()} of the AI Service invocation, not an id of this call.
     * @since 1.24.0
     */
    @Experimental
    public UUID invocationId() {
        return invocationId;
    }

    @Override
    public String toString() {
        return "ModerationModelErrorContext{" + "error="
                + error + ", moderationRequest="
                + moderationRequest + ", modelProvider="
                + modelProvider + ", attributes="
                + attributes + ", invocationId="
                + invocationId + '}';
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
        private ModerationRequest moderationRequest;
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
        public Builder moderationRequest(ModerationRequest moderationRequest) {
            this.moderationRequest = moderationRequest;
            return this;
        }

        /**
         * Required: {@link #build()} fails if it is not set.
         */
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

        /**
         * Sets the id of the AI Service invocation in which the call is made, see {@link #invocationId()} of the
         * context. LangChain4j sets it for the listeners; set it yourself only when you create the context yourself.
         *
         * @since 1.24.0
         */
        @Experimental
        public Builder invocationId(UUID invocationId) {
            this.invocationId = invocationId;
            return this;
        }

        public ModerationModelErrorContext build() {
            return new ModerationModelErrorContext(this);
        }
    }
}
