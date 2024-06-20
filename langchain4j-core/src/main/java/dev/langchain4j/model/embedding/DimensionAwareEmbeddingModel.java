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
     * Returns the dimension of known {@link Embedding} produced by this embedding model. if it's unknown, return null
     *
     * @return the dimension of known {@link Embedding}, null if unknown.
     */
    protected Integer getKnownDimension() {
        return null;
    }

    @Override
    public int dimension() {
        if (dimension != null) {
            return dimension;
        }

        Integer knownDimension = getKnownDimension();
        this.dimension = Optional.ofNullable(knownDimension).orElseGet(() -> embed("test").content().dimension());
        return this.dimension;
    }
}
