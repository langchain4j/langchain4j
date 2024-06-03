package dev.langchain4j.model.azure;

public enum AzureOpenAiEmbeddingModelName {

    TEXT_EMBEDDING_3_SMALL("text-embedding-3-small", "text-embedding-3-small"), // alias for the latest text-embedding-3-small model
    TEXT_EMBEDDING_3_SMALL_1("text-embedding-3-small-1", "text-embedding-3-small", "1"),
    TEXT_EMBEDDING_3_LARGE("text-embedding-3-large", "text-embedding-3-large"),
    TEXT_EMBEDDING_3_LARGE_1("text-embedding-3-large-1", "text-embedding-3-large", "1"),

    TEXT_EMBEDDING_ADA_002("text-embedding-ada-002", "text-embedding-ada-002"), // alias for the latest text-embedding-ada-002 model
    TEXT_EMBEDDING_ADA_002_1("text-embedding-ada-002-1", "text-embedding-ada-002", "1"),
    TEXT_EMBEDDING_ADA_002_2("text-embedding-ada-002-2", "text-embedding-ada-002", "2");

    private final String modelName;
    // Model type follows the com.knuddels.jtokkit.api.ModelType naming convention
    private final String modelType;
    private final String modelVersion;

    AzureOpenAiEmbeddingModelName(String modelName, String modelType) {
        this.modelName = modelName;
        this.modelType = modelType;
        this.modelVersion = null;
    }

    AzureOpenAiEmbeddingModelName(String modelName, String modelType, String modelVersion) {
        this.modelName = modelName;
        this.modelType = modelType;
        this.modelVersion = null;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelType() {
        return modelType;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    @Override
    public String toString() {
        return modelName;
    }
}
