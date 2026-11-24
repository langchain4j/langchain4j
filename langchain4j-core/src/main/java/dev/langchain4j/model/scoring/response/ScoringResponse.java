package dev.langchain4j.model.scoring.response;

import static dev.langchain4j.internal.Utils.copy;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Objects;

/**
 * The result of scoring a {@code ScoringRequest}: one relevance score per request document, in the same order,
 * together with the {@link ScoringResponseMetadata} (model name, token/billing usage).
 *
 * @since 1.20.0
 */
@Experimental
public class ScoringResponse {

    private final List<Double> scores;
    private final ScoringResponseMetadata metadata;

    protected ScoringResponse(Builder builder) {
        this.scores = copy(builder.scores);

        ScoringResponseMetadata.Builder<?> metadataBuilder = ScoringResponseMetadata.builder();
        if (builder.modelName != null) {
            validate(builder, "modelName");
            metadataBuilder.modelName(builder.modelName);
        }
        if (builder.tokenUsage != null) {
            validate(builder, "tokenUsage");
            metadataBuilder.tokenUsage(builder.tokenUsage);
        }
        this.metadata = builder.metadata != null ? builder.metadata : metadataBuilder.build();
    }

    /**
     * The relevance scores, one per request document, in the same order as {@code ScoringRequest.documents()}.
     */
    public List<Double> scores() {
        return scores;
    }

    public ScoringResponseMetadata metadata() {
        return metadata;
    }

    public String modelName() {
        return metadata.modelName();
    }

    public TokenUsage tokenUsage() {
        return metadata.tokenUsage();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScoringResponse that = (ScoringResponse) o;
        return Objects.equals(scores, that.scores) && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scores, metadata);
    }

    @Override
    public String toString() {
        return "ScoringResponse{scores=" + scores + ", metadata=" + metadata + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<Double> scores;
        private ScoringResponseMetadata metadata;

        private String modelName;
        private TokenUsage tokenUsage;

        public Builder scores(List<Double> scores) {
            this.scores = scores;
            return this;
        }

        public Builder metadata(ScoringResponseMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder tokenUsage(TokenUsage tokenUsage) {
            this.tokenUsage = tokenUsage;
            return this;
        }

        public ScoringResponse build() {
            return new ScoringResponse(this);
        }
    }

    private static void validate(Builder builder, String name) {
        if (builder.metadata != null) {
            throw new IllegalArgumentException(
                    "Cannot set both 'metadata' and '%s' on ScoringResponse".formatted(name));
        }
    }
}
