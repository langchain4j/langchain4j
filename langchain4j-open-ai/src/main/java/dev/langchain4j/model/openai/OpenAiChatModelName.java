package dev.langchain4j.model.openai;

public enum OpenAiChatModelName {

    GPT_3_5_TURBO("gpt-3.5-turbo"), // alias
    GPT_3_5_TURBO_1106("gpt-3.5-turbo-1106"),
    GPT_3_5_TURBO_0125("gpt-3.5-turbo-0125"),

    GPT_3_5_TURBO_16K("gpt-3.5-turbo-16k"), // alias

    GPT_4("gpt-4"), // alias
    GPT_4_0613("gpt-4-0613"),

    GPT_4_TURBO_PREVIEW("gpt-4-turbo-preview"), // alias
    GPT_4_1106_PREVIEW("gpt-4-1106-preview"),
    GPT_4_0125_PREVIEW("gpt-4-0125-preview"),
    GPT_4_TURBO("gpt-4-turbo"), // alias
    GPT_4_TURBO_2024_04_09("gpt-4-turbo-2024-04-09"),

    GPT_4_32K("gpt-4-32k"), // alias
    GPT_4_32K_0613("gpt-4-32k-0613"),

    GPT_4_O("gpt-4o"), // alias
    GPT_4_O_2024_05_13("gpt-4o-2024-05-13"),
    GPT_4_O_2024_08_06("gpt-4o-2024-08-06"),
    GPT_4_O_2024_11_20("gpt-4o-2024-11-20"),

    GPT_4_O_MINI("gpt-4o-mini"), // alias
    GPT_4_O_MINI_2024_07_18("gpt-4o-mini-2024-07-18"),

    O1("o1"), // alias
    O1_2024_12_17("o1-2024-12-17"),

    O1_MINI("o1-mini"), // alias
    O1_MINI_2024_09_12("o1-mini-2024-09-12"),

    O1_PREVIEW("o1-preview"), // alias
    O1_PREVIEW_2024_09_12("o1-preview-2024-09-12"),

    O3("o3"), // alias
    O3_2025_04_16("o3-2025-04-16"),

    O3_MINI("o3-mini"), // alias
    O3_MINI_2025_01_31("o3-mini-2025-01-31"),

    O4_MINI("o4-mini"), // alias
    O4_MINI_2025_04_16("o4-mini-2025-04-16"),

    GPT_4_1("gpt-4.1"), // alias
    GPT_4_1_2025_04_14("gpt-4.1-2025-04-14"),

    GPT_4_1_MINI("gpt-4.1-mini"), // alias
    GPT_4_1_MINI_2025_04_14("gpt-4.1-mini-2025-04-14"),

    GPT_4_1_NANO("gpt-4.1-nano"), // alias
    GPT_4_1_NANO_2025_04_14("gpt-4.1-nano-2025-04-14"),
    ;

    private final String stringValue;

    OpenAiChatModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
