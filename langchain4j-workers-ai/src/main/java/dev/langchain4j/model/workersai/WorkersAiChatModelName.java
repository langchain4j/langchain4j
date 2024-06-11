package dev.langchain4j.model.workersai;

/**
 * Enum for Workers AI Chat Model Name.
 */
public enum WorkersAiChatModelName {

    // ---------------------------------------------------------------------
    // Text Generation
    // https://developers.cloudflare.com/workers-ai/models/text-generation/
    // ---------------------------------------------------------------------

    /** Full precision (fp16) generative text model with 7 billion parameters from Met. */
    LLAMA2_7B_FULL("@cf/meta/llama-2-7b-chat-fp16"),
    /** Quantized (int8) generative text model with 7 billion parameters from Meta. */
    LLAMA2_7B_QUANTIZED("@cf/meta/llama-2-7b-chat-int8"),
    /** Instruct fine-tuned version of the Mistral-7b generative text model with 7 billion parameters. */
    CODELLAMA_7B_AWQ("@hf/thebloke/codellama-7b-instruct-awq"),
    /** Deepseek Coder is composed of a series of code language models, each trained from scratch on 2T tokens, with a composition of 87% code and 13% natural language in both English and Chinese.. */
    DEEPSEEK_CODER_6_7_BASE("@hf/thebloke/deepseek-coder-6.7b-base-awq"),
    /** Deepseek Coder is composed of a series of code language models, each trained from scratch on 2T tokens, with a composition of 87% code and 13% natural language in both English and Chinese.. */
    DEEPSEEK_CODER_MATH_7B_AWQ(" @hf/thebloke/deepseek-math-7b-awq"),
    /** DeepSeekMath is initialized with DeepSeek-Coder-v1.5 7B and continues pre-training on math-related tokens sourced from Common Crawl, together with natural language and code data for 500B tokens. */
    DEEPSEEK_CODER_MATH_7B_INSTRUCT("@hf/thebloke/deepseek-math-7b-instruct"),
    /** DeepSeekMath-Instruct 7B is a mathematically instructed tuning model derived from DeepSeekMath-Base 7B. DeepSeekMath is initialized with DeepSeek-Coder-v1.5 7B and continues pre-training on math-related tokens sourced from Common Crawl, together with natural language and code data for 500B tokens.. */
    MISTRAL_7B_INSTRUCT("@cf/mistral/mistral-7b-instruct-v0.1"),
    /** DiscoLM German 7b is a Mistral-based large language model with a focus on German-language applications. AWQ is an efficient, accurate and blazing-fast low-bit weight quantization method, currently supporting 4-bit quantization. */
    DISCOLM_GERMAN_7B_V1_AWQ("@cf/thebloke/discolm-german-7b-v1-awq"),
    /** Falcon-7B-Instruct is a 7B parameters causal decoder-only model built by TII based on Falcon-7B and finetuned on a mixture of chat/instruct datasets. */
    FALCOM_7B_INSTRUCT("@cf/tiiuae/falcon-7b-instruct"),
    /** This is a Gemma-2B base model that Cloudflare dedicates for inference with LoRA adapters. Gemma is a family of lightweight, state-of-the-art open models from Google, built from the same research and technology used to create the Gemini models. */
    GEMMA_2B_IT_LORA("@cf/google/gemma-2b-it-lora"),
    /** Gemma is a family of lightweight, state-of-the-art open models from Google, built from the same research and technology used to create the Gemini models. They are text-to-text, decoder-only large language models, available in English, with open weights, pre-trained variants, and instruction-tuned variants. */
    GEMMA_7B_IT("@hf/google/gemma-7b-it"),
    /** This is a Gemma-7B base model that Cloudflare dedicates for inference with LoRA adapters. Gemma is a family of lightweight, state-of-the-art open models from Google, built from the same research and technology used to create the Gemini models. */
    GEMMA_2B_IT_LORA_DUPLICATE("@cf/google/gemma-2b-it-lora"),
    /** Hermes 2 Pro on Mistral 7B is the new flagship 7B Hermes! Hermes 2 Pro is an upgraded, retrained version of Nous Hermes 2, consisting of an updated and cleaned version of the OpenHermes 2.5 Dataset, as well as a newly introduced Function Calling and JSON Mode dataset developed in-house. */
    HERMES_2_PRO_MISTRAL_7B("@hf/nousresearch/hermes-2-pro-mistral-7b"),
    /** Llama 2 13B Chat AWQ is an efficient, accurate and blazing-fast low-bit weight quantized Llama 2 variant. */
    LLAMA_2_13B_CHAT_AWQ("@hf/thebloke/llama-2-13b-chat-awq"),
    /** This is a Llama2 base model that Cloudflare dedicated for inference with LoRA adapters. Llama 2 is a collection of pretrained and fine-tuned generative text models ranging in scale from 7 billion to 70 billion parameters. This is the repository for the 7B fine-tuned model, optimized for dialogue use cases and converted for the Hugging Face Transformers format. */
    LLAMA_2_7B_CHAT_HF_LORA("@cf/meta-llama/llama-2-7b-chat-hf-lora"),
    /** Generation over generation, Meta Llama 3 demonstrates state-of-the-art performance on a wide range of industry benchmarks and offers new capabilities, including improved reasoning. */
    LLAMA_3_8B_INSTRUCT("@cf/meta/llama-3-8b-instruct"),
    /** Quantized (int4) generative text model with 8 billion parameters from Meta. */
    LLAMA_2_13B_CHAT_AWQ_DUPLICATE("@hf/thebloke/llama-2-13b-chat-awq"),
    /** Llama Guard is a model for classifying the safety of LLM prompts and responses, using a taxonomy of safety risks. */
    LLAMAGUARD_7B_AWQ("@hf/thebloke/llamaguard-7b-awq"),
    /** Quantized (int4) generative text model with 8 billion parameters from Meta. */
    META_LLAMA_3_8B_INSTRUCT("@hf/meta-llama/meta-llama-3-8b-instruct"),
    /** Mistral 7B Instruct v0.1 AWQ is an efficient, accurate and blazing-fast low-bit weight quantized Mistral variant. */
    MISTRAL_7B_INSTRUCT_V0_1_AWQ("@hf/thebloke/mistral-7b-instruct-v0.1-awq"),
    /** The Mistral-7B-Instruct-v0.2 Large Language Model (LLM) is an instruct fine-tuned version of the Mistral-7B-v0.2. Mistral-7B-v0.2 has the following changes compared to Mistral-7B-v0.1: 32k context window (vs 8k context in v0.1), rope-theta = 1e6, and no Sliding-Window Attention. */
    MISTRAL_7B_INSTRUCT_V0_2("@hf/mistral/mistral-7b-instruct-v0.2"),
    /** The Mistral-7B-Instruct-v0.2 Large Language Model (LLM) is an instruct fine-tuned version of the Mistral-7B-v0.2. */
    MISTRAL_7B_INSTRUCT_V0_2_LORA("@cf/mistral/mistral-7b-instruct-v0.2-lora"),
    /** This model is a fine-tuned 7B parameter LLM on the Intel Gaudi 2 processor from the mistralai/Mistral-7B-v0.1 on the open source dataset Open-Orca/SlimOrca. */
    NEURAL_CHAT_7B_V3_1_AWQ("@hf/thebloke/neural-chat-7b-v3-1-awq"),
    /** OpenChat is an innovative library of open-source language models, fine-tuned with C-RLFT - a strategy inspired by offline reinforcement learning. */
    OPENCHAT_3_5_0106("@cf/openchat/openchat-3.5-0106"),
    /** OpenHermes 2.5 Mistral 7B is a state of the art Mistral Fine-tune, a continuation of OpenHermes 2 model, which trained on additional code datasets. */
    OPENHERMES_2_5_MISTRAL_7B_AWQ("@hf/thebloke/openhermes-2.5-mistral-7b-awq"),
    /** Phi-2 is a Transformer-based model with a next-word prediction objective, trained on 1.4T tokens from multiple passes on a mixture of Synthetic and Web datasets for NLP and coding. */
    PHI_2("@cf/microsoft/phi-2"),
    /** Qwen1.5 is the improved version of Qwen, the large language model series developed by Alibaba Cloud. */
    QWEN1_5_0_5B_CHAT("@cf/qwen/qwen1.5-0.5b-chat"),
    /** Qwen1.5 is the improved version of Qwen, the large language model series developed by Alibaba Cloud. */
    QWEN1_5_1_8B_CHAT("@cf/qwen/qwen1.5-1.8b-chat"),
    /** Qwen1.5 is the improved version of Qwen, the large language model series developed by Alibaba Cloud. AWQ is an efficient, accurate and blazing-fast low-bit weight quantization method, currently supporting 4-bit quantization. */
    QWEN1_5_14B_CHAT_AWQ("@cf/qwen/qwen1.5-14b-chat-awq"),
    /** Qwen1.5 is the improved version of Qwen, the large language model series developed by Alibaba Cloud. AWQ is an efficient, accurate and blazing-fast low-bit weight quantization method, currently supporting 4-bit quantization. */
    QWEN1_5_7B_CHAT_AWQ("@cf/qwen/qwen1.5-7b-chat-awq"),
    /** This model is intended to be used by non-technical users to understand data inside their SQL databases. */
    SQLCODER_7B_2("@cf/defog/sqlcoder-7b-2"),
    /** We introduce Starling-LM-7B-beta, an open large language model (LLM) trained by Reinforcement Learning from AI Feedback (RLAIF). Starling-LM-7B-beta is trained from Openchat-3.5-0106 with our new reward model Nexusflow/Starling-RM-34B and policy optimization method Fine-Tuning Language Models from Human Preferences (PPO). */
    STARLING_LM_7B_BETA("@hf/nexusflow/starling-lm-7b-beta"),
    /** The TinyLlama project aims to pretrain a 1.1B Llama model on 3 trillion tokens. This is the chat model finetuned on top of TinyLlama/TinyLlama-1.1B-intermediate-step-1431k-3T. */
    TINYLLAMA_1_1B_CHAT_V1_0("@cf/tinyllama/tinyllama-1.1b-chat-v1.0"),
    /** Cybertron 7B v2 is a 7B MistralAI based model, best on it’s series. It was trained with SFT, DPO and UNA (Unified Neural Alignment) on multiple datasets. */
    UNA_CYBERTRON_7B_V2_BF16("@cf/fblgit/una-cybertron-7b-v2-bf16"),
    /** Zephyr 7B Beta AWQ is an efficient, accurate and blazing-fast low-bit weight quantized Zephyr model variant. */
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
