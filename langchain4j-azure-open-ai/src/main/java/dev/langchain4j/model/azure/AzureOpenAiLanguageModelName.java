package dev.langchain4j.model.azure;

public enum AzureOpenAiLanguageModelName {

    GPT_3_5_TURBO_INSTRUCT("gpt-35-turbo-instruct"), // alias for the latest gpt-3.5-turbo-instruct model
    GPT_3_5_TURBO_INSTRUCT_0914("gpt-35-turbo-instruct-0914"),

    TEXT_DAVINCI_002("davinci-002"),
    TEXT_DAVINCI_002_1("davinci-002-1");

    private final String stringValue;

    AzureOpenAiLanguageModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
