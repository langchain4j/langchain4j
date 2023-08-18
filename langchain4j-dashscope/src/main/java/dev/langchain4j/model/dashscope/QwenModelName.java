package dev.langchain4j.model.dashscope;

/**
 * The LLMs provided by Alibaba Cloud, performs better than most LLMs in Asia languages.
 */
public class QwenModelName {
    // Use with QwenChatModel and QwenLanguageModel
    public static final String QWEN_V1 = "qwen-v1";  // Qwen base model, 4k context.
    public static final String QWEN_PLUS_V1 = "qwen-plus-v1";  // Qwen plus model, 8k context.
    public static final String QWEN_7B_CHAT_V1 = "qwen-7b-chat-v1";  // Qwen open sourced 7-billion-parameters version, 4k context.
    public static final String QWEN_SPARK_V1 = "qwen-spark-v1";  // Qwen sft for conversation scene, 4k context.

    // Use with QwenEmbeddingModel
    public static final String TEXT_EMBEDDING_V1 = "text-embedding-v1";
}