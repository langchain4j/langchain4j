package dev.langchain4j.model.scoring.listener;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import java.util.Map;

/**
 * The context of a request to a scoring model: the request (the documents, the query and the parameters), the model
 * provider and attributes.
 * The attributes can be used to pass data between the methods of a {@link ScoringModelListener}, or between
 * multiple listeners.
 *
 * @since 1.23.0
 */
@Experimental
public final class ScoringModelRequestContext {

    private final ScoringRequest scoringRequest;
    private final ModelProvider modelProvider;
    private final Map<Object, Object> attributes;

    private ScoringModelRequestContext(Builder builder) {
        this.scoringRequest = ensureNotNull(builder.scoringRequest, "scoringRequest");
        this.modelProvider = builder.modelProvider;
        this.attributes = ensureNotNull(builder.attributes, "attributes");
    }

    /**
     * @return the request: the texts of the documents to score, the query and the parameters, including the model
     * name ({@code null} when the model sets none in its default request parameters and the request sets none, for
     * example with {@code DecisionScoringModel}).
     */
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
     * @return the attributes map, shared with the response and error contexts of the same call.
     */
    public Map<Object, Object> attributes() {
        return attributes;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ScoringModelRequestContext}.
     *
     * @since 1.23.0
     */
    @Experimental
    public static final class Builder {

        private ScoringRequest scoringRequest;
        private ModelProvider modelProvider;
        private Map<Object, Object> attributes;

        Builder() {}

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

        public ScoringModelRequestContext build() {
            return new ScoringModelRequestContext(this);
        }
    }
}
