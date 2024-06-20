package dev.langchain4j.model.vertexai;

import java.util.HashMap;
import java.util.Map;

public enum VertexAiEmbeddingModelName {

    TEXTEMBEDDING_GECKO_001("textembedding-gecko@001", 768),
    TEXTEMBEDDING_GECKO_002("textembedding-gecko@002", 768),
    TEXTEMBEDDING_GECKO_003("textembedding-gecko@003", 768);

    private final String stringValue;
    private final Integer dimension;

    VertexAiEmbeddingModelName(String stringValue,
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

    private static final Map<String, Integer> KNOWN_DIMENSION = new HashMap<>(VertexAiEmbeddingModelName.values().length);

    static {
        for (VertexAiEmbeddingModelName embeddingModelName : VertexAiEmbeddingModelName.values()) {
            KNOWN_DIMENSION.put(embeddingModelName.toString(), embeddingModelName.dimension());
        }
    }

    public static Integer knownDimension(String modelName) {
        return KNOWN_DIMENSION.get(modelName);
    }
}
