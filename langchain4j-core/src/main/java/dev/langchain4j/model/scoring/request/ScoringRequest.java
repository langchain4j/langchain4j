package dev.langchain4j.model.scoring.request;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.util.List;
import java.util.Objects;

/**
 * A request to score (re-rank) a list of {@link #documents()} against a {@link #query()}.
 * <p>
 * A scoring model produces one relevance score per document; the resulting {@code ScoringResponse} carries the
 * scores in the same order as {@code documents}. Optional per-call {@link ScoringRequestParameters parameters}
 * (such as the model name) are carried alongside.
 *
 * @since 1.19.0
 */
@Experimental
public class ScoringRequest {

    private final List<String> documents;
    private final String query;
    private final ScoringRequestParameters parameters;

    protected ScoringRequest(Builder builder) {
        this.documents = copy(ensureNotNull(builder.documents, "documents"));
        this.query = ensureNotNull(builder.query, "query");
        this.parameters = getOrDefault(builder.parameters, ScoringRequestParameters.EMPTY);
    }

    /**
     * The documents to score against the {@link #query()}. The response scores are in this order.
     */
    public List<String> documents() {
        return documents;
    }

    /**
     * The query against which the {@link #documents()} are scored.
     */
    public String query() {
        return query;
    }

    /**
     * The per-call parameters of this request; never {@code null} ({@link ScoringRequestParameters#EMPTY} if unset).
     */
    public ScoringRequestParameters parameters() {
        return parameters;
    }

    /**
     * Convenience accessor for {@code parameters().modelName()}.
     */
    public String modelName() {
        return parameters.modelName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScoringRequest that = (ScoringRequest) o;
        return Objects.equals(documents, that.documents)
                && Objects.equals(query, that.query)
                && Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documents, query, parameters);
    }

    @Override
    public String toString() {
        return "ScoringRequest{documents=" + documents + ", query=" + query + ", parameters=" + parameters + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<String> documents;
        private String query;
        private ScoringRequestParameters parameters;

        public Builder documents(List<String> documents) {
            this.documents = documents;
            return this;
        }

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder parameters(ScoringRequestParameters parameters) {
            this.parameters = parameters;
            return this;
        }

        public ScoringRequest build() {
            return new ScoringRequest(this);
        }
    }
}
