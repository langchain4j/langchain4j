package dev.langchain4j.model.azure;

import java.util.HashMap;
import java.util.Map;

public class AzureOpenAiModelName {

    // Use with AzureOpenAiChatModel and AzureOpenAiStreamingChatModel
    public static final String GPT_3_5_TURBO = "gpt-3.5-turbo"; // alias for the latest model
    public static final String GPT_3_5_TURBO_0613 = "gpt-3.5-turbo-0613"; // 4k context, functions
    public static final String GPT_3_5_TURBO_0125 = "gpt-3.5-turbo-0125"; // 4k context, functions
    public static final String GPT_3_5_TURBO_1106 = "gpt-3.5-turbo-1106"; // 16k context, functions

    public static final String GPT_3_5_TURBO_16K = "gpt-3.5-turbo-16k"; // alias for the latest model
    public static final String GPT_3_5_TURBO_16K_0613 = "gpt-3.5-turbo-16k-0613"; // 16k context, functions

    public static final String GPT_4 = "gpt-4"; // alias for the latest model
    public static final String GPT_4_1106_PREVIEW = "gpt-4-1106-preview"; // 8k context
    public static final String GPT_4_0613 = "gpt-4-0613"; // 8k context, functions

    public static final String GPT_4_32K = "gpt-4-32k"; // alias for the latest model
    public static final String GPT_4_32K_0613 = "gpt-4-32k-0613"; // 32k context, functions


    // Use with AzureOpenAiLanguageModel and AzureOpenAiStreamingLanguageModel
    public static final String TEXT_DAVINCI_002 = "text-davinci-002";

    public static final String GPT_3_5_TURBO_INSTRUCT = "gpt-3.5-turbo-instruct";


    // Use with AzureOpenAiEmbeddingModel
    public static final String TEXT_EMBEDDING_ADA_002 = "text-embedding-ada-002";
    public static final String TEXT_EMBEDDING_3_SMALL = "text-embedding-3-small";
    public static final String TEXT_EMBEDDING_3_LARGE = "text-embedding-3-large";

    // Use with AzureOpenAiImageModel
    public static final String DALL_E_3 = "DALL_E_3";

    public static Map<String, Integer> embeddingModelDimensionMap() {
        return new HashMap<String, Integer>() {{
            put(TEXT_DAVINCI_002, 1536);
            put(TEXT_EMBEDDING_3_SMALL, 1536);
            put(TEXT_EMBEDDING_3_LARGE, 3072);
        }};
    }

}
