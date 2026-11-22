package dev.langchain4j.model.openai;

public enum OpenAiChatModelName {
    GPT_4_O("gpt-4o"), // alias
    GPT_4_O_2024_08_06("gpt-4o-2024-08-06"),
    GPT_4_O_2024_11_20("gpt-4o-2024-11-20"),

    GPT_4_O_MINI("gpt-4o-mini"), // alias
    GPT_4_O_MINI_2024_07_18("gpt-4o-mini-2024-07-18"),

    O3("o3"), // alias
    O3_2025_04_16("o3-2025-04-16"),

    GPT_4_1("gpt-4.1"), // alias
    GPT_4_1_2025_04_14("gpt-4.1-2025-04-14"),

    GPT_4_1_MINI("gpt-4.1-mini"), // alias
    GPT_4_1_MINI_2025_04_14("gpt-4.1-mini-2025-04-14"),

    GPT_5("gpt-5"),
    GPT_5_MINI("gpt-5-mini"),
    GPT_5_NANO("gpt-5-nano"),

    GPT_5_1("gpt-5.1"),

    GPT_6_ASTRA("gpt-6-astra"),
    GPT_6_LUNA("gpt-6-luna"),

    GPT_6_1_SOL("gpt-6.1-sol"),
    GPT_6_1_TERRA("gpt-6.1-terra");

    private final String stringValue;

    OpenAiChatModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
