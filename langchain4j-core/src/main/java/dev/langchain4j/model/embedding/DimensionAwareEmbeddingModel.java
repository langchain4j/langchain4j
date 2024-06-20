package dev.langchain4j.model.embedding;

import java.util.HashMap;
import java.util.Map;

/**
 * A dimension aware embedding model
 */
public abstract class DimensionAwareEmbeddingModel implements EmbeddingModel {

    /**
     * dimension of embedding
     */
    protected Integer dimension;
    /**
     * dimension map of known embedding model's name and its embedding's dimension
     */
    protected Map<String, Integer> dimensionMap = new HashMap<>();

    /**
     * A map contains known model's name and its embedding's dimension
     *
     * @return A map, key is the common represented model name, value is its embedding's dimension
     */
    protected Map<String, Integer> dimensionMap() {
        return dimensionMap;
    }

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
