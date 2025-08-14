package dev.langchain4j.model.azure;

public enum AzureOpenAiAudioModelName {

    WHISPER_1("-1", "whisper-1"); // whisper model

    private final String modelName;
    // Used for model identification
    private final String modelType;
    private final String modelVersion;

    AzureOpenAiAudioModelName(String modelName, String modelType) {
        this.modelName = modelName;
        this.modelType = modelType;
        this.modelVersion = null;
    }

    AzureOpenAiAudioModelName(String modelName, String modelType, String modelVersion) {
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
