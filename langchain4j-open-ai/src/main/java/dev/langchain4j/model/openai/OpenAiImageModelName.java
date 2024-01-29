package dev.langchain4j.model.openai;

public enum OpenAiImageModelName {

    DALL_E_2("dall-e-2"),
    DALL_E_3("dall-e-3");

    private final String stringValue;

    OpenAiImageModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
