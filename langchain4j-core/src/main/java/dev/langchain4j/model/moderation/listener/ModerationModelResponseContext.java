package dev.langchain4j.model.moderation.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;

import java.util.Map;
import java.util.UUID;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * The moderation model response context.
 * It contains {@link ModerationResponse}, corresponding {@link ModerationRequest}, {@link ModelProvider} and attributes.
 * The attributes can be used to pass data between methods of a {@link ModerationModelListener}
 * or between multiple {@link ModerationModelListener}s.
 */
public class ModerationModelResponseContext {

    private final ModerationResponse moderationResponse;
    private final ModerationRequest moderationRequest;
    private final ModelProvider modelProvider;
    private final Map<Object, Object> attributes;
    private final UUID invocationId;

    /**
     * @deprecated use {@link #builder()} instead
     */
    @Deprecated(since = "1.24.0")
    public ModerationModelResponseContext(
            ModerationResponse moderationResponse,
            ModerationRequest moderationRequest,
            ModelProvider modelProvider,
            Map<Object, Object> attributes) {
        this(builder()
                .moderationResponse(moderationResponse)
                .moderationRequest(moderationRequest)
                .modelProvider(modelProvider)
                .attributes(attributes));
    }
    private ModerationModelResponseContext(Builder builder) {
        this.moderationResponse = ensureNotNull(builder.moderationResponse, "moderationResponse");
        this.moderationRequest = ensureNotNull(builder.moderationRequest, "moderationRequest");
        this.modelProvider = ensureNotNull(builder.modelProvider, "modelProvider");
        this.attributes = ensureNotNull(builder.attributes, "attributes");
        this.invocationId = builder.invocationId;
    }

    /**
     * @return The moderation response.
     */
    public ModerationResponse moderationResponse() {
        return moderationResponse;
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
     * @return the id of the AI Service invocation this call belongs to: the one whose work (a tool, a guardrail,
     * retrieval, ...) makes the call, otherwise {@code null}. It is the {@link InvocationContext#invocationId()} of the
     * AI Service invocation, not an id of this call.
     * @since 1.24.0
     */
    @Experimental
    public UUID invocationId() {
        return invocationId;
    }

    @Override
    public String toString() {
        return "ModerationModelResponseContext{" + "moderationResponse="
                + moderationResponse + ", moderationRequest="
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

        private ModerationResponse moderationResponse;
        private ModerationRequest moderationRequest;
        private ModelProvider modelProvider;
        private Map<Object, Object> attributes;
        private UUID invocationId;

        Builder() {}

        /**
         * Required: {@link #build()} fails if it is not set.
         */
        public Builder moderationResponse(ModerationResponse moderationResponse) {
            this.moderationResponse = moderationResponse;
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

        @Experimental
        public Builder invocationId(UUID invocationId) {
            this.invocationId = invocationId;
            return this;
        }

        public ModerationModelResponseContext build() {
            return new ModerationModelResponseContext(this);
        }
    }
}
