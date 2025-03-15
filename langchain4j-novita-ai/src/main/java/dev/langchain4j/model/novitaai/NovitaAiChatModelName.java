package dev.langchain4j.model.novitaai;

/**
 * Enum for Novita AI Chat Model Name.
 */
public enum NovitaAiChatModelName {

    // ---------------------------------------------------------------------
    // LLM API
    // https://novita.ai/docs/guides/llm-api
    // ---------------------------------------------------------------------

    /** DeepSeek R1 is the latest open-source model released by the DeepSeek team, featuring impressive reasoning capabilities. */
    DEEPSEEK_R1_TURBO("deepseek/deepseek-r1-turbo"),

    /** DeepSeek-V3 is the latest model from the DeepSeek team with enhanced instruction following and coding abilities. */
    DEEPSEEK_V3_TURBO("deepseek/deepseek-v3-turbo"),

    /** QwQ-32B reasoning model achieving competitive performance against state-of-the-art models. */
    QWEN_QWQ_32B("qwen/qwq-32b"),

    /** Meta's efficient 8B instruct-tuned model with strong human evaluation performance. */
    LLAMA_3_1_8B_INSTRUCT("meta-llama/llama-3.1-8b-instruct"),

    /** DeepSeek R1 base model with 64k context size. */
    DEEPSEEK_R1("deepseek/deepseek-r1"),

    /** Latest DeepSeek V3 model with 64k context window. */
    DEEPSEEK_V3("deepseek/deepseek_v3"),

    /** Meta's high-quality 70B instruct-tuned dialogue model. */
    LLAMA_3_1_70B_INSTRUCT("meta-llama/llama-3.1-70b-instruct"),

    /** Meta's multilingual 70B model optimized for dialogue. */
    LLAMA_3_3_70B_INSTRUCT("meta-llama/llama-3.3-70b-instruct"),

    /** Mistral's 12B multilingual model with 128k context. */
    MISTRAL_NEMO("mistralai/mistral-nemo"),

    /** Distilled Qwen 14B model using DeepSeek R1 outputs. */
    DEEPSEEK_R1_DISTILL_QWEN_14B("deepseek/deepseek-r1-distill-qwen-14b"),

    /** Distilled Qwen 32B model with state-of-the-art benchmarks. */
    DEEPSEEK_R1_DISTILL_QWEN_32B("deepseek/deepseek-r1-distill-qwen-32b"),

    /** Llama-based distilled 70B model. */
    DEEPSEEK_R1_DISTILL_LLAMA_70B("deepseek/deepseek-r1-distill-llama-70b"),

    /** Specialized roleplay model with deep immersion capabilities. */
    L3_8B_STHENO_V3_2("Sao10K/L3-8B-Stheno-v3.2"),

    /** Popular 13B model with enhanced writing capability. */
    MYTHOMAX_L2_13B("gryphe/mythomax-l2-13b"),

    /** Cost-efficient distilled Llama 8B model. */
    DEEPSEEK_R1_DISTILL_LLAMA_8B("deepseek/deepseek-r1-distill-llama-8b"),

    /** Qwen 2.5 series 72B instruct model. */
    QWEN_2_5_72B_INSTRUCT("qwen/qwen-2.5-72b-instruct"),

    /** Meta's entry-level 8B instruct model. */
    LLAMA_3_8B_INSTRUCT("meta-llama/llama-3-8b-instruct"),

    /** Microsoft's advanced 8x22B mixture-of-experts model. */
    WIZARDLM_2_8X22B("microsoft/wizardlm-2-8x22b"),

    /** Google's efficient 9B open-source model. */
    GEMMA_2_9B_IT("google/gemma-2-9b-it"),

    /** Industry-standard 7B Mistral model. */
    MISTRAL_7B_INSTRUCT("mistralai/mistral-7b-instruct"),

    /** Meta's flagship 70B instruct model. */
    LLAMA_3_70B_INSTRUCT("meta-llama/llama-3-70b-instruct"),

    /** OpenChat's RL-tuned 7B model. */
    OPENCHAT_7B("openchat/openchat-7b"),

    /** Hermes 2 Pro with enhanced function calling. */
    HERMES_2_PRO_LLAMA_3_8B("nousresearch/hermes-2-pro-llama-3-8b"),

    /** Creative writing-focused 70B model. */
    L3_70B_EURYALE_V2_1("sao10k/l3-70b-euryale-v2.1"),

    /** High-performance Mixtral 8x22B vision model. */
    DOLPHIN_MIXTRAL_8X22B("cognitivecomputations/dolphin-mixtral-8x22b"),

    /** Long-context roleplay specialist. */
    AIROBOROS_L2_70B("jondurbin/airoboros-l2-70b"),

    /** Nous-Hermes fine-tuned Llama2 model. */
    NOUS_HERMES_LLAMA2_13B("nousresearch/nous-hermes-llama2-13b"),

    /** OpenHermes 2.5 Mistral variant. */
    OPENHERMES_2_5_MISTRAL_7B("teknium/openhermes-2.5-mistral-7b"),

    /** Storytelling-optimized 70B model. */
    MIDNIGHT_ROSE_70B("sophosympatheia/midnight-rose-70b"),

    /** Generalist Llama3-based merge. */
    L3_8B_LUNARIS("sao10k/l3-8b-lunaris"),

    /** Qwen's multimodal vision-language model. */
    QWEN_2_VL_72B_INSTRUCT("qwen/qwen-2-vl-72b-instruct"),

    /** Meta's compact 1B instruct model. */
    LLAMA_3_2_1B_INSTRUCT("meta-llama/llama-3.2-1b-instruct"),

    /** Vision-capable 11B instruct model. */
    LLAMA_3_2_11B_VISION_INSTRUCT("meta-llama/llama-3.2-11b-vision-instruct"),

    /** Efficient 3B instruct model. */
    LLAMA_3_2_3B_INSTRUCT("meta-llama/llama-3.2-3b-instruct"),

    /** BF16 optimized 8B variant. */
    LLAMA_3_1_8B_INSTRUCT_BF16("meta-llama/llama-3.1-8b-instruct-bf16"),

    /** Enhanced Euryale v2.2 creative model. */
    L31_70B_EURYALE_V2_2("sao10k/l31-70b-euryale-v2.2"),

    /** Qwen2 7B instruct model. */
    QWEN_2_7B_INSTRUCT("qwen/qwen-2-7b-instruct");

    private final String modelId;

    NovitaAiChatModelName(String modelId) {
        this.modelId = modelId;
    }

    public String getModelId() {
        return modelId;
    }

    public static boolean contains(String modelName) {
        for (NovitaAiChatModelName novitaAiChatModelName : NovitaAiChatModelName.values()) {
            if (novitaAiChatModelName.getModelId().equals(modelName)) {
                return true;
            }
        }
        return false;
    }
}

