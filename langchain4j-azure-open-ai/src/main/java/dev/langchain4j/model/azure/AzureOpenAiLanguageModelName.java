package dev.langchain4j.model.azure;

public enum AzureOpenAiLanguageModelName {

    GPT_3_5_TURBO_INSTRUCT("gpt-3.5-turbo-instruct"),

    TEXT_DAVINCI_002("text-davinci-002");

    private final String stringValue;

    AzureOpenAiLanguageModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
