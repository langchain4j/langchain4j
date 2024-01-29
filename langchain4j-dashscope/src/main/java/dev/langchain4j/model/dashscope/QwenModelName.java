package dev.langchain4j.model.dashscope;

/**
 * The LLMs provided by Alibaba Cloud, performs better than most LLMs in Asia languages.
 */
public class QwenModelName {

    // Use with QwenChatModel and QwenLanguageModel
    public static final String QWEN_TURBO = "qwen-turbo";  // Qwen base model, 4k context.
    public static final String QWEN_PLUS = "qwen-plus";  // Qwen plus model, 8k context.
    public static final String QWEN_MAX = "qwen-max";  // Qwen max model, 200-billion-parameters, 8k context.
    public static final String QWEN_7B_CHAT = "qwen-7b-chat";  // Qwen open sourced 7-billion-parameters version
    public static final String QWEN_14B_CHAT = "qwen-14b-chat";  // Qwen open sourced 14-billion-parameters version
    public static final String QWEN_VL_PLUS = "qwen-vl-plus";  // Qwen multi-modal model, supports image and text information.
    public static final String QWEN_VL_MAX = "qwen-vl-max";  // Qwen multi-modal model, offers optimal performance on a wider range of complex tasks.

    // Use with QwenEmbeddingModel
    public static final String TEXT_EMBEDDING_V1 = "text-embedding-v1";  // Support: en, zh, es, fr, pt, id
    public static final String TEXT_EMBEDDING_V2 = "text-embedding-v2";  // Support: en, zh, es, fr, pt, id, ja, ko, de, ru
}