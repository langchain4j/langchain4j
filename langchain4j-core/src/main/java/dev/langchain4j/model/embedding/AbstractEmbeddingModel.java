package dev.langchain4j.model.embedding;

import java.util.Map;
import java.util.Optional;

/**
 * An Embedding Model which contains
 */
public abstract class AbstractEmbeddingModel implements EmbeddingModel {

    /**
     * embedding model dimension
     */
    protected Integer dimension;

    /**
     * A map contains known model name and its dimension
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

        return dimensionMap().compute(modelName(), (key, value) -> {
            this.dimension = Optional.ofNullable(value).orElse(embed("test").content().dimension());
            return dimension;
        });
    }
}
