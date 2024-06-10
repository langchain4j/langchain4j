package dev.langchain4j.model.workersai;

/**
 * Group model name in a class to ease the usage.
 * It has been preferred as an Enum for flexibility.
 */
@SuppressWarnings("unused")
public class WorkersAiModelName {

    /**
     * Max token for text generation
     */
    public static final int MAX_TOKENS =  512;

    // ---------------------------------------------------------------------
    // Text Generation
    // https://developers.cloudflare.com/workers-ai/models/text-generation/
    // ---------------------------------------------------------------------
    /**
     * Model names
     */
    public static final String LLAMA2_7B_FULL = "@cf/meta/llama-2-7b-chat-fp16";
    /**
     * Model names
     */
    public static final String LLAMA2_7B_QUANTIZED = "@cf/meta/llama-2-7b-chat-int8";
    /**
     * Model names
     */
    public static final String LLAMA2_7B_AWQ = "@hf/thebloke/codellama-7b-instruct-awq";
     /**
     * Model names
     */
    public static final String DEEPSEEK_CODER_6_7_BASE = "@hf/thebloke/deepseek-coder-6.7b-base-awq";
    /**
     * Model names
     */
    public static final String DEEPSEEK_CODER_MATH_7B_AWQ = " @hf/thebloke/deepseek-math-7b-awq";
    /**
     * Model names
     */
    public static final String DEEPSEEK_CODER_MATH_7B_INSTRUCT = "@hf/thebloke/deepseek-math-7b-instruct";
    /**
     * Model names
     */
    public static final String MISTRAL_7B_INSTRUCT = "@cf/mistral/mistral-7b-instruct-v0.1";
    /**
     * Model names
     */
    public static final String DISCOLM_GERMAN_7B_V1_AWQ = "@cf/thebloke/discolm-german-7b-v1-awq";
    /**
     * Model names
     */
    public static final String FALCOM_7B_INSTRUCT = "@cf/tiiuae/falcon-7b-instruct";
    /**
     * Model names
     */
    public static final String GEMMA_2B_IT_LORA = "@cf/google/gemma-2b-it-lora";
    /**
     * Model names
     */
    public static final String GEMMA_7B_IT = "@hf/google/gemma-7b-it";
    /**
     * Model names
     */
    public static final String GEMMA_2B_IT_LORA_DUPLICATE = "@cf/google/gemma-2b-it-lora";
    /**
     * Model names
     */
    public static final String HERMES_2_PRO_MISTRAL_7B = "@hf/nousresearch/hermes-2-pro-mistral-7b";
    /**
     * Model names
     */
    public static final String LLAMA_2_13B_CHAT_AWQ = "@hf/thebloke/llama-2-13b-chat-awq";
    /**
     * Model names
     */
    public static final String LLAMA_2_7B_CHAT_HF_LORA = "@cf/meta-llama/llama-2-7b-chat-hf-lora";
    /**
     * Model names
     */
    public static final String LLAMA_3_8B_INSTRUCT = "@cf/meta/llama-3-8b-instruct";
    /**
     * Model names
     */
    public static final String LLAMA_2_13B_CHAT_AWQ_DUPLICATE = "@hf/thebloke/llama-2-13b-chat-awq";
    /**
     * Model names
     */
    public static final String LLAMAGUARD_7B_AWQ = "@hf/thebloke/llamaguard-7b-awq";
    /**
     * Model names
     */
    public static final String META_LLAMA_3_8B_INSTRUCT = "@hf/meta-llama/meta-llama-3-8b-instruct";
    /**
     * Model names
     */
    public static final String MISTRAL_7B_INSTRUCT_V0_1_AWQ = "@hf/thebloke/mistral-7b-instruct-v0.1-awq";
    /**
     * Model names
     */
    public static final String MISTRAL_7B_INSTRUCT_V0_2 = "@hf/mistral/mistral-7b-instruct-v0.2";
    /**
     * Model names
     */
    public static final String MISTRAL_7B_INSTRUCT_V0_2_LORA = "@cf/mistral/mistral-7b-instruct-v0.2-lora";
    /**
     * Model names
     */
    public static final String NEURAL_CHAT_7B_V3_1_AWQ = "@hf/thebloke/neural-chat-7b-v3-1-awq";
    /**
     * Model names
     */
    public static final String NEURAL_CHAT_7B_V3_1_AWQ_DUPLICATE = "@hf/thebloke/neural-chat-7b-v3-1-awq";
    /**
     * Model names
     */
    public static final String OPENCHAT_3_5_0106 = "@cf/openchat/openchat-3.5-0106";
    /**
     * Model names
     */
    public static final String OPENHERMES_2_5_MISTRAL_7B_AWQ = "@hf/thebloke/openhermes-2.5-mistral-7b-awq";
    /**
     * Model names
     */
    public static final String PHI_2 = "@cf/microsoft/phi-2";
    /**
     * Model names
     */
    public static final String QWEN1_5_0_5B_CHAT = "@cf/qwen/qwen1.5-0.5b-chat";
    /**
     * Model names
     */
    public static final String QWEN1_5_1_8B_CHAT = "@cf/qwen/qwen1.5-1.8b-chat";
    /**
     * Model names
     */
    public static final String QWEN1_5_14B_CHAT_AWQ = "@cf/qwen/qwen1.5-14b-chat-awq";
    /**
     * Model names
     */
    public static final String QWEN1_5_7B_CHAT_AWQ = "@cf/qwen/qwen1.5-7b-chat-awq";
    /**
     * Model names
     */
    public static final String SQLCODER_7B_2 = "@cf/defog/sqlcoder-7b-2";
    /**
     * Model names
     */
    public static final String STARLING_LM_7B_BETA = "@hf/nexusflow/starling-lm-7b-beta";
    /**
     * Model names
     */
    public static final String TINYLLAMA_1_1B_CHAT_V1_0 = "@cf/tinyllama/tinyllama-1.1b-chat-v1.0";
    /**
     * Model names
     */
    public static final String UNA_CYBERTRON_7B_V2_BF16 = "@cf/fblgit/una-cybertron-7b-v2-bf16";
    /**
     * Model names
     */
    public static final String ZEPHYR_7B_BETA_AWQ = "@hf/thebloke/zephyr-7b-beta-awq";


    // ---------------------------------------------------------------------
    // Text Embeddings
    // https://developers.cloudflare.com/workers-ai/models/text-embeddings/
    // ---------------------------------------------------------------------

    /**
     * Model names
     * maxToken: 512, dimension 384
     */
    public static final String BAAI_EMBEDDING_SMALL = "@cf/baai/bge-small-en-v1.5";
    /**
     * Model names
     * maxToken: 512, dimension 768
     */
    public static final String BAAI_EMBEDDING_BASE = "@cf/baai/bge-base-en-v1.5";
    /**
     * Model names
     * maxToken: 512, dimension 1024
     */
    public static final String BAAI_EMBEDDING_LARGE = "@cf/baai/bge-large-en-v1.5";

    // ---------------------------------------------------------------------
    // Text to image
    // https://developers.cloudflare.com/workers-ai/models/text-to-image/
    // ---------------------------------------------------------------------

    /**
     * Model names
     */
    public static final String STABLE_DIFFUSION_XL = "@cf/stabilityai/stable-diffusion-xl-base-1.0";

    /**
     * Model names
     */
    public static final String DREAMSHAPER_8_LCM = "@cf/lykon/dreamshaper-8-lcm";
    /**
     * Model names
     */
    public static final String STABLE_DIFFUSION_V1_5_IMG2IMG = "@cf/runwayml/stable-diffusion-v1-5-img2img";
    /**
     * Model names
     */
    public static final String STABLE_DIFFUSION_V1_5_INPAINTING = "@cf/runwayml/stable-diffusion-v1-5-inpainting";
    /**
     * Model names
     */
    public static final String STABLE_DIFFUSION_XL_LIGHTNING = "@cf/bytedance/stable-diffusion-xl-lightning";

    /**
     * Hide constructor for utility classes.
     */
    private WorkersAiModelName() {
    }
}
