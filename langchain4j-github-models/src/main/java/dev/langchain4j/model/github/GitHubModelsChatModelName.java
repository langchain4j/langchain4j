package dev.langchain4j.model.github;

public enum GitHubModelsChatModelName {

    GPT_4_O("gpt-4o", "gpt-4"),
    GPT_4_O_MINI("gpt-4o-mini", "gpt-4"),
    //O1_MINI("o1-mini", "o1"),

    PHI_3_5_MINI_INSTRUCT("Phi-3.5-mini-instruct", "phi"),
    PHI_3_5_VISION_INSTRUCT("Phi-3.5-vision-instruct", "phi"),
    PHI_3_MEDIUM_INSTRUCT_128K("Phi-3-medium-128k-instruct", "phi"),
    PHI_3_MEDIUM_INSTRUCT_4K("Phi-3-medium-4k-instruct", "phi"),
    PHI_3_MINI_INSTRUCT_128K("Phi-3-mini-128k-instruct", "phi"),
    PHI_3_MINI_INSTRUCT_4K("Phi-3-mini-4k-instruct", "phi"),
    PHI_3_SMALL_INSTRUCT_128K("Phi-3-small-128k-instruct", "phi"),
    PHI_3_SMALL_INSTRUCT_8K("Phi-3-small-8k-instruct", "phi"),

    AI21_JAMBA_1_5_LARGE("ai21-jamba-1.5-large", "ai21"),
    AI21_JAMBA_1_5_MINI("ai21-jamba-1.5-mini", "ai21"),
    AI21_JAMBA_INSTRUCT("ai21-jamba-instruct", "ai21"),

    COHERE_COMMAND_R("cohere-command-r", "cohere"),
    COHERE_COMMAND_R_PLUS("cohere-command-r-plus", "cohere"),

    META_LLAMA_3_1_405B_INSTRUCT("meta-llama-3.1-405b-instruct", "meta-llama"),
    META_LLAMA_3_1_70B_INSTRUCT("meta-llama-3.1-70b-instruct", "meta-llama"),
    META_LLAMA_3_1_8B_INSTRUCT("meta-llama-3.1-8b-instruct", "meta-llama"),
    META_LLAMA_3_70B_INSTRUCT("meta-llama-3-70b-instruct", "meta-llama"),
    META_LLAMA_3_8B_INSTRUCT("meta-llama-3-8b-instruct", "meta-llama"),

    MISTRAL_NEMO("Mistral-nemo", "mistral"),
    MISTRAL_LARGE("Mistral-large", "mistral"),
    MISTRAL_LARGE_2407("Mistral-large-2407", "mistral"),
    MISTRAL_SMALL("Mistral-small", "mistral");

    private final String modelName;
    private final String modelType;

    GitHubModelsChatModelName(String modelName, String modelType) {
        this.modelName = modelName;
        this.modelType = modelType;
    }

    @Override
    public String toString() {
        return modelName;
    }
}
