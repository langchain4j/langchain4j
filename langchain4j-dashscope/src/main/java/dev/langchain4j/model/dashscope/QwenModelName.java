package dev.langchain4j.model.dashscope;

/**
 * The LLMs provided by Alibaba Cloud, performs better than most LLMs in Asia languages.
 */
public class QwenModelName {
    // Use with QwenChatModel and QwenLanguageModel
    public static final String QWEN_TURBO = "qwen-turbo";  // Qwen base model, 4k context.
    public static final String QWEN_PLUS = "qwen-plus";  // Qwen plus model, 8k context.
    public static final String QWEN_MAX = "qwen-max";  // Qwen max model, 200-billion-parameters, 8k context.
    public static final String QWEN_MAX_LONGCONTEXT = "qwen-max-longcontext";  // Qwen max model, 200-billion-parameters, 30k context.
    public static final String QWEN_7B_CHAT = "qwen-7b-chat";  // Qwen open sourced 7-billion-parameters model
    public static final String QWEN_14B_CHAT = "qwen-14b-chat";  // Qwen open sourced 14-billion-parameters model
    public static final String QWEN_72B_CHAT = "qwen-72b-chat";  // Qwen open sourced 72-billion-parameters model
    public static final String QWEN1_5_7B_CHAT = "qwen1.5-7b-chat";  // Qwen open sourced 7-billion-parameters model (v1.5)
    public static final String QWEN1_5_14B_CHAT = "qwen1.5-14b-chat";  // Qwen open sourced 14-billion-parameters model (v1.5)
    public static final String QWEN1_5_32B_CHAT = "qwen1.5-32b-chat";  // Qwen open sourced 32-billion-parameters model (v1.5)
    public static final String QWEN1_5_72B_CHAT = "qwen1.5-72b-chat";  // Qwen open sourced 72-billion-parameters model (v1.5)
    public static final String QWEN2_0_5B_INSTRUCT = "qwen2-0.5b-instruct";  // Qwen open sourced 0.5-billion-parameters model (v2)
    public static final String QWEN2_1_5B_INSTRUCT = "qwen2-1.5b-instruct";  // Qwen open sourced 1.5-billion-parameters model (v2)
    public static final String QWEN2_7B_INSTRUCT = "qwen2-7b-instruct";  // Qwen open sourced 7-billion-parameters model (v2)
    public static final String QWEN2_72B_INSTRUCT = "qwen2-72b-instruct";  // Qwen open sourced 72-billion-parameters model (v2)
    public static final String QWEN2_57B_A14B_INSTRUCT = "qwen2-57b-a14b-instruct";  // Qwen open sourced 57-billion-parameters and 14-billion-activation-parameters MOE model (v2)
    public static final String QWEN_VL_PLUS = "qwen-vl-plus";  // Qwen multi-modal model, supports image and text information.
    public static final String QWEN_VL_MAX = "qwen-vl-max";  // Qwen multi-modal model, offers optimal performance on a wider range of complex tasks.
    public static final String QWEN_AUDIO_CHAT = "qwen-audio-chat";  // Qwen open sourced speech model, sft for chatting.
    public static final String QWEN2_AUDIO_INSTRUCT = "qwen2-audio-instruct";  // Qwen open sourced speech model (v2)

    // Use with QwenEmbeddingModel
    public static final String TEXT_EMBEDDING_V1 = "text-embedding-v1";  // Support: en, zh, es, fr, pt, id
    public static final String TEXT_EMBEDDING_V2 = "text-embedding-v2";  // Support: en, zh, es, fr, pt, id, ja, ko, de, ru
}