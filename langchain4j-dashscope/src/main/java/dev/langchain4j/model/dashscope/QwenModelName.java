package dev.langchain4j.model.dashscope;

/**
 * The LLMs provided by Alibaba Cloud, performs better than most LLMs in Asia languages.
 */
public class QwenModelName {

    // Use with QwenChatModel and QwenLanguageModel
    public static final String QWEN_TURBO = "qwen-turbo";  // Qwen base model, 4k context.
    public static final String QWEN_PLUS = "qwen-plus";  // Qwen plus model, 8k context.
    public static final String QWEN_SPARK_V1 = "qwen-spark-v1";  // Qwen sft for ai character scene, 4k context.
    public static final String QWEN_SPARK_V2 = "qwen-spark-v2";  // Qwen sft for ai character scene, 8k context.
    public static final String QWEN_7B_CHAT = "qwen-7b-chat";  // Qwen open sourced 7-billion-parameters version
    public static final String QWEN_14B_CHAT = "qwen-14b-chat";  // Qwen open sourced 14-billion-parameters version

    // Use with QwenEmbeddingModel
    public static final String TEXT_EMBEDDING_V1 = "text-embedding-v1";
}