package dev.langchain4j.model.workersai;

/**
 * Enum for Workers AI Chat Model Name.
 */
public enum WorkersAiChatModelName {

    // ---------------------------------------------------------------------
    // Text Generation
    // https://developers.cloudflare.com/workers-ai/models/text-generation/
    // ---------------------------------------------------------------------

    LLAMA2_7B_FULL("@cf/meta/llama-2-7b-chat-fp16"),
    LLAMA2_7B_QUANTIZED("@cf/meta/llama-2-7b-chat-int8"),
    CODELLAMA_7B_AWQ("@hf/thebloke/codellama-7b-instruct-awq"),
    DEEPSEEK_CODER_6_7_BASE("@hf/thebloke/deepseek-coder-6.7b-base-awq"),
    DEEPSEEK_CODER_MATH_7B_AWQ(" @hf/thebloke/deepseek-math-7b-awq"),
    DEEPSEEK_CODER_MATH_7B_INSTRUCT("@hf/thebloke/deepseek-math-7b-instruct"),
    MISTRAL_7B_INSTRUCT("@cf/mistral/mistral-7b-instruct-v0.1"),
    DISCOLM_GERMAN_7B_V1_AWQ("@cf/thebloke/discolm-german-7b-v1-awq"),
    FALCOM_7B_INSTRUCT("@cf/tiiuae/falcon-7b-instruct"),
    GEMMA_2B_IT_LORA("@cf/google/gemma-2b-it-lora"),
    GEMMA_7B_IT("@hf/google/gemma-7b-it"),
    GEMMA_2B_IT_LORA_DUPLICATE("@cf/google/gemma-2b-it-lora"),
    HERMES_2_PRO_MISTRAL_7B("@hf/nousresearch/hermes-2-pro-mistral-7b"),
    LLAMA_2_13B_CHAT_AWQ("@hf/thebloke/llama-2-13b-chat-awq"),
    LLAMA_2_7B_CHAT_HF_LORA("@cf/meta-llama/llama-2-7b-chat-hf-lora"),
    LLAMA_3_8B_INSTRUCT("@cf/meta/llama-3-8b-instruct"),
    LLAMA_2_13B_CHAT_AWQ_DUPLICATE("@hf/thebloke/llama-2-13b-chat-awq"),
    LLAMAGUARD_7B_AWQ("@hf/thebloke/llamaguard-7b-awq"),
    META_LLAMA_3_8B_INSTRUCT("@hf/meta-llama/meta-llama-3-8b-instruct"),
    MISTRAL_7B_INSTRUCT_V0_1_AWQ("@hf/thebloke/mistral-7b-instruct-v0.1-awq"),
    MISTRAL_7B_INSTRUCT_V0_2("@hf/mistral/mistral-7b-instruct-v0.2"),
    MISTRAL_7B_INSTRUCT_V0_2_LORA("@cf/mistral/mistral-7b-instruct-v0.2-lora"),
    NEURAL_CHAT_7B_V3_1_AWQ("@hf/thebloke/neural-chat-7b-v3-1-awq"),
    NEURAL_CHAT_7B_V3_1_AWQ_DUPLICATE("@hf/thebloke/neural-chat-7b-v3-1-awq"),
    OPENCHAT_3_5_0106("@cf/openchat/openchat-3.5-0106"),
    OPENHERMES_2_5_MISTRAL_7B_AWQ("@hf/thebloke/openhermes-2.5-mistral-7b-awq"),
    PHI_2("@cf/microsoft/phi-2"),
    QWEN1_5_0_5B_CHAT("@cf/qwen/qwen1.5-0.5b-chat"),
    QWEN1_5_1_8B_CHAT("@cf/qwen/qwen1.5-1.8b-chat"),
    QWEN1_5_14B_CHAT_AWQ("@cf/qwen/qwen1.5-14b-chat-awq"),
    QWEN1_5_7B_CHAT_AWQ("@cf/qwen/qwen1.5-7b-chat-awq"),
    SQLCODER_7B_2("@cf/defog/sqlcoder-7b-2"),
    STARLING_LM_7B_BETA("@hf/nexusflow/starling-lm-7b-beta"),
    TINYLLAMA_1_1B_CHAT_V1_0("@cf/tinyllama/tinyllama-1.1b-chat-v1.0"),
    UNA_CYBERTRON_7B_V2_BF16("@cf/fblgit/una-cybertron-7b-v2-bf16"),
    ZEPHYR_7B_BETA_AWQ("@hf/thebloke/zephyr-7b-beta-awq");

    private final String stringValue;

    WorkersAiChatModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }


}
