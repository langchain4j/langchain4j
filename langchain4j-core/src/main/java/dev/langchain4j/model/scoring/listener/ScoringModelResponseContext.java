package dev.langchain4j.model.scoring.listener;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.response.ScoringResponse;
import java.util.Map;
import java.util.UUID;

/**
 * The context of a response from a scoring model: the response (the scores and the metadata, such as the token
 * usage), the corresponding request, the model provider and attributes.
 * The attributes can be used to pass data between the methods of a {@link ScoringModelListener}, or between
 * multiple listeners.
 *
 * @since 1.24.0
 */
@Experimental
public final class ScoringModelResponseContext {

    private final ScoringResponse scoringResponse;
    private final ScoringRequest scoringRequest;
    private final ModelProvider modelProvider;
    private final Map<Object, Object> attributes;
    private final UUID invocationId;

    private ScoringModelResponseContext(Builder builder) {
        this.scoringResponse = ensureNotNull(builder.scoringResponse, "scoringResponse");
        this.scoringRequest = ensureNotNull(builder.scoringRequest, "scoringRequest");
        this.modelProvider = builder.modelProvider;
        this.attributes = ensureNotNull(builder.attributes, "attributes");
        this.invocationId = builder.invocationId;
    }

    /**
     * @return the response: one score per document, in the order of {@link ScoringRequest#documents()}, and the
     * metadata: the model name (the one reported by the provider, or the requested one when the provider does not
     * report it) and the token usage ({@code null} when the model does not report it). The unit of the token usage
     * depends on the provider: for example, Cohere reports billed search units, not tokens.
     */
    public ScoringResponse scoringResponse() {
        return scoringResponse;
    }

    public ScoringRequest scoringRequest() {
        return scoringRequest;
    }

    /**
     * @return the provider of the model, or {@code null} when the model's {@link ScoringModel#provider()} returns
     * {@code null}.
     */
    public ModelProvider modelProvider() {
        return modelProvider;
    }

    /**
     * @return the attributes map, shared with the request context of the same call.
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

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ScoringModelResponseContext}.
     *
     * @since 1.24.0
     */
    @Experimental
    public static final class Builder {

        private ScoringResponse scoringResponse;
        private ScoringRequest scoringRequest;
        private ModelProvider modelProvider;
        private Map<Object, Object> attributes;
        private UUID invocationId;

        Builder() {}

        public Builder scoringResponse(ScoringResponse scoringResponse) {
            this.scoringResponse = scoringResponse;
            return this;
        }

        public Builder scoringRequest(ScoringRequest scoringRequest) {
            this.scoringRequest = scoringRequest;
            return this;
        }

        public Builder modelProvider(ModelProvider modelProvider) {
            this.modelProvider = modelProvider;
            return this;
        }

        public Builder attributes(Map<Object, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        /**
         * @since 1.24.0
         */
        @Experimental
        public Builder invocationId(UUID invocationId) {
            this.invocationId = invocationId;
            return this;
        }

        public ScoringModelResponseContext build() {
            return new ScoringModelResponseContext(this);
        }
    }
}
