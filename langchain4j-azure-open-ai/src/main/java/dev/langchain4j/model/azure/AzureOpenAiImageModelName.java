package dev.langchain4j.model.azure;

public enum AzureOpenAiImageModelName {

    DALL_E_3("dall-e-3");

    private final String stringValue;

    AzureOpenAiImageModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
