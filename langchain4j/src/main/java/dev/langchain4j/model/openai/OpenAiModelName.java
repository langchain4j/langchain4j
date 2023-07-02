package dev.langchain4j.model.openai;

public class OpenAiModelName {

    // Use with OpenAiChatModel and OpenAiStreamingChatModel
    public static final String GPT_3_5_TURBO = "gpt-3.5-turbo";
    public static final String GPT_4 = "gpt-4";

    // Use with OpenAiLanguageModel and OpenAiStreamingLanguageModel
    public static final String TEXT_DAVINCI_003 = "text-davinci-003";

    // Use with OpenAiEmbeddingModel
    public static final String TEXT_EMBEDDING_ADA_002 = "text-embedding-ada-002";

    // Use with OpenAiModerationModel
    public static final String TEXT_MODERATION_STABLE = "text-moderation-stable";
    public static final String TEXT_MODERATION_LATEST = "text-moderation-latest";
}
