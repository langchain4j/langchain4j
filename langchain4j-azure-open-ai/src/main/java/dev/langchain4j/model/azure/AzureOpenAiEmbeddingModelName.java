package dev.langchain4j.model.azure;

import java.util.HashMap;
import java.util.Map;

public enum AzureOpenAiEmbeddingModelName {

    TEXT_EMBEDDING_3_SMALL("text-embedding-3-small", "text-embedding-3-small", 1536), // alias for the latest text-embedding-3-small model
    TEXT_EMBEDDING_3_SMALL_1("text-embedding-3-small-1", "text-embedding-3-small", "1", 1536),
    TEXT_EMBEDDING_3_LARGE("text-embedding-3-large", "text-embedding-3-large", 3072),
    TEXT_EMBEDDING_3_LARGE_1("text-embedding-3-large-1", "text-embedding-3-large", "1", 3072),

    TEXT_EMBEDDING_ADA_002("text-embedding-ada-002", "text-embedding-ada-002", 1536), // alias for the latest text-embedding-ada-002 model
    TEXT_EMBEDDING_ADA_002_1("text-embedding-ada-002-1", "text-embedding-ada-002", "1", 1536),
    TEXT_EMBEDDING_ADA_002_2("text-embedding-ada-002-2", "text-embedding-ada-002", "2", 1536);

    private final String modelName;
    // Model type follows the com.knuddels.jtokkit.api.ModelType naming convention
    private final String modelType;
    private final String modelVersion;
    private final Integer dimension;

    AzureOpenAiEmbeddingModelName(String modelName, String modelType, Integer dimension) {
        this.modelName = modelName;
        this.modelType = modelType;
        this.modelVersion = null;
        this.dimension = dimension;
    }

    AzureOpenAiEmbeddingModelName(String modelName, String modelType, String modelVersion, Integer dimension) {
        this.modelName = modelName;
        this.modelType = modelType;
        this.modelVersion = modelVersion;
        this.dimension = dimension;
    }

    public String modelName() {
        return modelName;
    }

    public String modelType() {
        return modelType;
    }

    public String modelVersion() {
        return modelVersion;
    }

    @Override
    public String toString() {
        return modelName;
    }

    public Integer dimension() {
        return dimension;
    }

    private static final Map<String, Integer> KNOWN_DIMENSION = new HashMap<>(AzureOpenAiEmbeddingModelName.values().length);

    static {
        for (AzureOpenAiEmbeddingModelName embeddingModelName : AzureOpenAiEmbeddingModelName.values()) {
            KNOWN_DIMENSION.put(embeddingModelName.toString(), embeddingModelName.dimension());
        }
    }

    public static Integer knownDimension(String modelName) {
        return KNOWN_DIMENSION.get(modelName);
    }
}
