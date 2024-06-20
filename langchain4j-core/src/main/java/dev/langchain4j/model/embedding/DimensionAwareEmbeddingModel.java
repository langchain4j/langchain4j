package dev.langchain4j.model.embedding;

import dev.langchain4j.data.embedding.Embedding;

import java.util.Optional;

/**
 * A dimension aware embedding model
 */
public abstract class DimensionAwareEmbeddingModel implements EmbeddingModel {

    /**
     * dimension of embedding
     */
    protected Integer dimension;

    /**
     * When known (e.g., can be derived from the model name), returns the dimension of the {@link Embedding} produced by this embedding model. Otherwise, it returns {@code null}.
     *
     * @return the known dimension of the {@link Embedding}, or {@code null} if unknown.
     */
    protected Integer knownDimension() {
        return null;
    }

    @Override
    public int dimension() {
        if (dimension != null) {
            return dimension;
        }

        Integer knownDimension = knownDimension();
        this.dimension = Optional.ofNullable(knownDimension).orElseGet(() -> embed("test").content().dimension());
        return this.dimension;
    }
}
