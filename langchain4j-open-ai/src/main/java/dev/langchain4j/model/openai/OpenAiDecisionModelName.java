package dev.langchain4j.model.openai;

public enum OpenAiDecisionModelName {
    GPT_6_LUNA("gpt-6-luna");

    private final String stringValue;

    OpenAiDecisionModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
