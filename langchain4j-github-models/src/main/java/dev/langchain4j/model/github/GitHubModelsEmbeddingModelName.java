package dev.langchain4j.model.github;

import java.util.HashMap;
import java.util.Map;

public enum GitHubModelsEmbeddingModelName {

    TEXT_EMBEDDING_3_SMALL("text-embedding-3-small", "text-embedding-3-small", 1536),
    TEXT_EMBEDDING_3_LARGE("text-embedding-3-large", "text-embedding-3-large", 3072),

    COHERE_EMBED_V3_ENGLISH("cohere-embed-v3-english", "cohere-embed-v3-english", 1024),
    COHERE_EMBED_V3_MULTILINGUAL("cohere-embed-v3-multilingual", "cohere-embed-v3-multilingual", 1024);

    private final String modelName;
    // Model type follows the com.knuddels.jtokkit.api.ModelType naming convention
    private final String modelType;
    private final String modelVersion;
    private final Integer dimension;

    GitHubModelsEmbeddingModelName(String modelName, String modelType, Integer dimension) {
        this.modelName = modelName;
        this.modelType = modelType;
        this.modelVersion = null;
        this.dimension = dimension;
    }

    GitHubModelsEmbeddingModelName(String modelName, String modelType, String modelVersion, Integer dimension) {
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

    private static final Map<String, Integer> KNOWN_DIMENSION = new HashMap<>(GitHubModelsEmbeddingModelName.values().length);

    static {
        for (GitHubModelsEmbeddingModelName embeddingModelName : GitHubModelsEmbeddingModelName.values()) {
            KNOWN_DIMENSION.put(embeddingModelName.toString(), embeddingModelName.dimension());
        }
    }

    public static Integer knownDimension(String modelName) {
        return KNOWN_DIMENSION.get(modelName);
    }
}
