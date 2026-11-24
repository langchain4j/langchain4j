package dev.langchain4j.model.decision.listener;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.request.DecisionRequest;
import java.util.Map;
import java.util.UUID;

/**
 * The context of an error of a decision model: the error, the corresponding request, the model provider and
 * attributes.
 * The attributes can be used to pass data between the methods of a {@link DecisionModelListener}, or between
 * multiple listeners.
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionModelErrorContext {

    private final Throwable error;
    private final DecisionRequest decisionRequest;
    private final ModelProvider modelProvider;
    private final Map<Object, Object> attributes;
    private final UUID invocationId;

    /**
     * @deprecated use {@link #builder()} instead
     */
    @Deprecated(since = "1.24.0")
    public DecisionModelErrorContext(
            Throwable error,
            DecisionRequest decisionRequest,
            ModelProvider modelProvider,
            Map<Object, Object> attributes) {
        this(builder()
                .error(error)
                .decisionRequest(decisionRequest)
                .modelProvider(modelProvider)
                .attributes(attributes));
    }
    private DecisionModelErrorContext(Builder builder) {
        this.error = ensureNotNull(builder.error, "error");
        this.decisionRequest = ensureNotNull(builder.decisionRequest, "decisionRequest");
        this.modelProvider = ensureNotNull(builder.modelProvider, "modelProvider");
        this.attributes = ensureNotNull(builder.attributes, "attributes");
        this.invocationId = builder.invocationId;
    }

    public Throwable error() {
        return error;
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
        private DecisionRequest decisionRequest;
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

        public DecisionModelErrorContext build() {
            return new DecisionModelErrorContext(this);
        }
    }
}
