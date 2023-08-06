package dev.langchain4j.model.openai;

public class OpenAiModelName {

    // Use with OpenAiChatModel and OpenAiStreamingChatModel
    public static final String GPT_3_5_TURBO = "gpt-3.5-turbo"; // alias for the latest model
    public static final String GPT_3_5_TURBO_0301 = "gpt-3.5-turbo-0301"; // 4k context
    public static final String GPT_3_5_TURBO_0613 = "gpt-3.5-turbo-0613"; // 4k context, functions

    public static final String GPT_3_5_TURBO_16K = "gpt-3.5-turbo-16k"; // alias for the latest model
    public static final String GPT_3_5_TURBO_16K_0613 = "gpt-3.5-turbo-16k-0613"; // 16k context, functions

    public static final String GPT_4 = "gpt-4"; // alias for the latest model
    public static final String GPT_4_0314 = "gpt-4-0314"; // 8k context
    public static final String GPT_4_0613 = "gpt-4-0613"; // 8k context, functions

    public static final String GPT_4_32K = "gpt-4-32k"; // alias for the latest model
    public static final String GPT_4_32K_0314 = "gpt-4-32k-0314"; // 32k context
    public static final String GPT_4_32K_0613 = "gpt-4-32k-0613"; // 32k context, functions


    // Use with OpenAiLanguageModel and OpenAiStreamingLanguageModel
    public static final String TEXT_DAVINCI_003 = "text-davinci-003";


    // Use with OpenAiEmbeddingModel
    public static final String TEXT_EMBEDDING_ADA_002 = "text-embedding-ada-002";


    // Use with OpenAiModerationModel
    public static final String TEXT_MODERATION_STABLE = "text-moderation-stable";
    public static final String TEXT_MODERATION_LATEST = "text-moderation-latest";
}
