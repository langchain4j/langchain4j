package dev.langchain4j.model.openai;

public enum OpenAiLanguageModelName {

    GPT_3_5_TURBO_INSTRUCT("gpt-3.5-turbo-instruct");

    private final String stringValue;

    OpenAiLanguageModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
