package dev.langchain4j.model.huggingface;

public final class HuggingFaceModelName {

    private HuggingFaceModelName() {}

    // Use with HuggingFaceChatModel and HuggingFaceLanguageModel
    public static final String TII_UAE_FALCON_7B_INSTRUCT = "tiiuae/falcon-7b-instruct";
    public static final String MICROSOFT_PHI3_MINI_4K_INSTRUCT = "microsoft/Phi-3-mini-4k-instruct";

    // Use with HuggingFaceEmbeddingModel
    public static final String SENTENCE_TRANSFORMERS_ALL_MINI_LM_L6_V2 = "sentence-transformers/all-MiniLM-L6-v2";
}
