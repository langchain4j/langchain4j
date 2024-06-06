package dev.langchain4j.model.azure;

public enum AzureOpenAiImageModelName {

    DALL_E_3("dall-e-3", "dall-e-3"), // alias for the latest dall-e-3 model
    DALL_E_3_30("dall-e-3-30", "dall-e-3","30");

    private final String modelName;
    // Model type follows the com.knuddels.jtokkit.api.ModelType naming convention
    private final String modelType;
    private final String modelVersion;

    AzureOpenAiImageModelName(String modelName, String modelType) {
        this.modelName = modelName;
        this.modelType = modelType;
        this.modelVersion = null;
    }

    AzureOpenAiImageModelName(String modelName, String modelType, String modelVersion) {
        this.modelName = modelName;
        this.modelType = modelType;
        this.modelVersion = modelVersion;
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
}
