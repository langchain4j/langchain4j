package dev.langchain4j.model.openai;

/**
 * This class is deprecated. Use one of the following enums instead:
 * <pre>
 * {@link OpenAiChatModelName}
 * {@link OpenAiEmbeddingModelName}
 * {@link OpenAiImageModelName}
 * {@link OpenAiLanguageModelName}
 * {@link OpenAiModerationModelName}
 * </pre>
 */
@Deprecated
public class OpenAiModelName {

    // Use with OpenAiChatModel and OpenAiStreamingChatModel
    public static final String GPT_3_5_TURBO = "gpt-3.5-turbo"; // alias for the latest model
    public static final String GPT_3_5_TURBO_0301 = "gpt-3.5-turbo-0301"; // 4k context
    public static final String GPT_3_5_TURBO_0613 = "gpt-3.5-turbo-0613"; // 4k context, functions
    public static final String GPT_3_5_TURBO_1106 = "gpt-3.5-turbo-1106"; // 16k context, parallel functions

    public static final String GPT_3_5_TURBO_16K = "gpt-3.5-turbo-16k"; // alias for the latest model
    public static final String GPT_3_5_TURBO_16K_0613 = "gpt-3.5-turbo-16k-0613"; // 16k context, functions

    public static final String GPT_4 = "gpt-4"; // alias for the latest model
    public static final String GPT_4_0314 = "gpt-4-0314"; // 8k context
    public static final String GPT_4_0613 = "gpt-4-0613"; // 8k context, functions

    public static final String GPT_4_32K = "gpt-4-32k"; // alias for the latest model
    public static final String GPT_4_32K_0314 = "gpt-4-32k-0314"; // 32k context
    public static final String GPT_4_32K_0613 = "gpt-4-32k-0613"; // 32k context, functions

    public static final String GPT_4_1106_PREVIEW = "gpt-4-1106-preview"; // 128k context, parallel functions
    public static final String GPT_4_VISION_PREVIEW = "gpt-4-vision-preview"; // 128k context, vision

    // Use with OpenAiLanguageModel and OpenAiStreamingLanguageModel
    public static final String GPT_3_5_TURBO_INSTRUCT = "gpt-3.5-turbo-instruct";

    // Use with OpenAiEmbeddingModel
    public static final String TEXT_EMBEDDING_ADA_002 = "text-embedding-ada-002";

    // Use with OpenAiModerationModel
    public static final String TEXT_MODERATION_STABLE = "text-moderation-stable";
    public static final String TEXT_MODERATION_LATEST = "text-moderation-latest";

    // Use with OpenAiImageModel
    public static final String DALL_E_2 = "dall-e-2"; // anyone still needs that? :)
    public static final String DALL_E_3 = "dall-e-3";
}
