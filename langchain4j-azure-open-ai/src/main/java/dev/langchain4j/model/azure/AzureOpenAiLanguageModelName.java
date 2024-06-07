package dev.langchain4j.model.azure;

public enum AzureOpenAiLanguageModelName {

    GPT_3_5_TURBO_INSTRUCT("gpt-35-turbo-instruct", "gpt-3.5-turbo"), // alias for the latest gpt-3.5-turbo-instruct model
    GPT_3_5_TURBO_INSTRUCT_0914("gpt-35-turbo-instruct-0914", "gpt-3.5-turbo", "0914"), // 4k context, functions

    TEXT_DAVINCI_002("davinci-002", "text-davinci-002"),
    TEXT_DAVINCI_002_1("davinci-002-1", "text-davinci-002", "1"),;

    private final String modelName;
    // Model type follows the com.knuddels.jtokkit.api.ModelType naming convention
    private final String modelType;
    private final String modelVersion;

    AzureOpenAiLanguageModelName(String modelName, String modelType) {
        this.modelName = modelName;
        this.modelType = modelType;
        this.modelVersion = null;
    }

    AzureOpenAiLanguageModelName(String modelName, String modelType, String modelVersion) {
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
