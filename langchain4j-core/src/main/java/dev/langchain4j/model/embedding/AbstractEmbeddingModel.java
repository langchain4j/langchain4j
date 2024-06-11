package dev.langchain4j.model.embedding;

import java.util.Map;

/**
 * An Embedding Model which contains
 */
public abstract class AbstractEmbeddingModel implements EmbeddingModel {

    /**
     * embedding model dimension
     */
    protected Integer dimension;

    /**
     * A map contains known model's name and its dimension
     *
     * @return A map, key is the common represented model name, value is its dimension
     */
    protected abstract Map<String, Integer> dimensionMap();

    /**
     * Get embedding model's name
     *
     * @return embedding model's name
     */
    protected abstract String modelName();

    @Override
    public int dimension() {
        if (dimension != null) {
            return dimension;
        }

        // get known model's dimension first, otherwise embed "test" to get dimension
        if (dimensionMap().containsKey(modelName())) {
            this.dimension = dimensionMap().get(modelName());
        } else {
            this.dimension = embed("test").content().dimension();
        }

        return this.dimension;
    }
}
