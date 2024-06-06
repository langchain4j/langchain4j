package dev.langchain4j.model.azure;

public enum AzureOpenAiImageModelName {

    DALL_E_3("dall-e-3"), // alias for the latest dall-e-3 model
    DALL_E_3_30("dall-e-3-30");

    private final String stringValue;

    AzureOpenAiImageModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
