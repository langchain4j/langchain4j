package dev.langchain4j.model.voyageai;

import java.util.HashMap;
import java.util.Map;

public enum VoyageAiEmbeddingModelName {
    VOYAGE_3("voyage-3", 1024),
    VOYAGE_3_LITE("voyage-3-lite", 512),
    VOYAGE_3_LARGE("voyage-3-large", 1024),

    VOYAGE_FINANCE_2("voyage-finance-2", 1024),

    VOYAGE_MULTILINGUAL_2("voyage-multilingual-2", 1024),

    VOYAGE_LAW_2("voyage-law-2", 1024),

    VOYAGE_CODE_2("voyage-code-2", 1536),
    VOYAGE_CODE_3("voyage-code-3", 1024);

    private final String stringValue;
    private final Integer dimension;

    VoyageAiEmbeddingModelName(String stringValue, Integer dimension) {
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

    private static final Map<String, Integer> KNOWN_DIMENSION =
            new HashMap<>(VoyageAiEmbeddingModelName.values().length);

    static {
        for (VoyageAiEmbeddingModelName embeddingModelName : VoyageAiEmbeddingModelName.values()) {
            KNOWN_DIMENSION.put(embeddingModelName.toString(), embeddingModelName.dimension());
        }
    }

    public static Integer knownDimension(String modelName) {
        return KNOWN_DIMENSION.get(modelName);
    }
}
