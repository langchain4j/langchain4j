package dev.langchain4j.model.embedding.response;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import java.util.List;
import java.util.Objects;

/**
 * The result of embedding an {@link dev.langchain4j.model.embedding.request.EmbeddingRequest}: the
 * {@link Embedding}s produced for the request's inputs (in the same order) together with the
 * {@link EmbeddingResponseMetadata} such as the model name and token usage.
 *
 * @since 1.18.0
 */
@Experimental
public class EmbeddingResponse {

    private final List<Embedding> embeddings;
    private final EmbeddingResponseMetadata metadata;

    protected EmbeddingResponse(Builder builder) {
        this.embeddings = copy(builder.embeddings);
        this.metadata = getOrDefault(builder.metadata, () -> EmbeddingResponseMetadata.builder().build());
    }

    public List<Embedding> embeddings() {
        return embeddings;
    }

    public EmbeddingResponseMetadata metadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddingResponse that = (EmbeddingResponse) o;
        return Objects.equals(embeddings, that.embeddings) && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(embeddings, metadata);
    }

    @Override
    public String toString() {
        return "EmbeddingResponse{embeddings=" + embeddings + ", metadata=" + metadata + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<Embedding> embeddings;
        private EmbeddingResponseMetadata metadata;

        public Builder embeddings(List<Embedding> embeddings) {
            this.embeddings = embeddings;
            return this;
        }

        public Builder metadata(EmbeddingResponseMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public EmbeddingResponse build() {
            return new EmbeddingResponse(this);
        }
    }
}
