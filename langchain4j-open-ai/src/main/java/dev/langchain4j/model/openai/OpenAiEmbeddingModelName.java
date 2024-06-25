package dev.langchain4j.model.openai;

import java.util.HashMap;
import java.util.Map;

public enum OpenAiEmbeddingModelName {

    TEXT_EMBEDDING_3_SMALL("text-embedding-3-small", 1536),
    TEXT_EMBEDDING_3_LARGE("text-embedding-3-large", 3072),

    TEXT_EMBEDDING_ADA_002("text-embedding-ada-002", 1536);

    private final String stringValue;
    private final Integer dimension;

    OpenAiEmbeddingModelName(String stringValue,
                             Integer dimension) {
        this.stringValue = stringValue;
        this.dimension = dimension;
    }

    @Override
    public String toString() {
        return stringValue;
    }

    public Integer dimension() {
        return dimension;
    }

    private static final Map<String, Integer> KNOWN_DIMENSION = new HashMap<>(OpenAiEmbeddingModelName.values().length);

    static {
        for (OpenAiEmbeddingModelName embeddingModelName : OpenAiEmbeddingModelName.values()) {
            KNOWN_DIMENSION.put(embeddingModelName.toString(), embeddingModelName.dimension());
        }
    }

    public static Integer knownDimension(String modelName) {
        return KNOWN_DIMENSION.get(modelName);
    }
}
