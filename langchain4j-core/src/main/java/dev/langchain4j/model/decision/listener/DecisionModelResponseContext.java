package dev.langchain4j.model.decision.listener;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.util.Map;
import java.util.UUID;

/**
 * The context of a response from a decision model: the response, the corresponding request, the model
 * provider and attributes.
 * The attributes can be used to pass data between the methods of a {@link DecisionModelListener}, or between
 * multiple listeners.
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionModelResponseContext {

    private final DecisionResponse decisionResponse;
    private final DecisionRequest decisionRequest;
    private final ModelProvider modelProvider;
    private final Map<Object, Object> attributes;
    private final UUID invocationId;

    /**
     * @deprecated use {@link #builder()} instead
     */
    @Deprecated(since = "1.24.0")
    public DecisionModelResponseContext(
            DecisionResponse decisionResponse,
            DecisionRequest decisionRequest,
            ModelProvider modelProvider,
            Map<Object, Object> attributes) {
        this(builder()
                .decisionResponse(decisionResponse)
                .decisionRequest(decisionRequest)
                .modelProvider(modelProvider)
                .attributes(attributes));
    }
    private DecisionModelResponseContext(Builder builder) {
        this.decisionResponse = ensureNotNull(builder.decisionResponse, "decisionResponse");
        this.decisionRequest = ensureNotNull(builder.decisionRequest, "decisionRequest");
        this.modelProvider = ensureNotNull(builder.modelProvider, "modelProvider");
        this.attributes = ensureNotNull(builder.attributes, "attributes");
        this.invocationId = builder.invocationId;
    }

    public DecisionResponse decisionResponse() {
        return decisionResponse;
    }

    public DecisionRequest decisionRequest() {
        return decisionRequest;
    }

    public ModelProvider modelProvider() {
        return modelProvider;
    }

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

        private DecisionResponse decisionResponse;
        private DecisionRequest decisionRequest;
        private ModelProvider modelProvider;
        private Map<Object, Object> attributes;
        private UUID invocationId;

        Builder() {}

        /**
         * Required: {@link #build()} fails if it is not set.
         */
        public Builder decisionResponse(DecisionResponse decisionResponse) {
            this.decisionResponse = decisionResponse;
            return this;
        }

        /**
         * Required: {@link #build()} fails if it is not set.
         */
        public Builder decisionRequest(DecisionRequest decisionRequest) {
            this.decisionRequest = decisionRequest;
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

        public Builder invocationId(UUID invocationId) {
            this.invocationId = invocationId;
            return this;
        }

        public DecisionModelResponseContext build() {
            return new DecisionModelResponseContext(this);
        }
    }
}
