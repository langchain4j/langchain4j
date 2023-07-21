package dev.langchain4j.model.inprocess;

public enum InProcessEmbeddingModelType {

    /**
     * SentenceTransformers all-MiniLM-L6-v2 model.
     * More details: https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2
     */
    ALL_MINILM_L6_V2,

    /**
     * SentenceTransformers all-MiniLM-L6-v2 model (quantized).
     * More details: https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2
     */
    ALL_MINILM_L6_V2_Q,

    /**
     * E5-small-v2 model.
     * More details: https://huggingface.co/intfloat/e5-small-v2
     */
    E5_SMALL_V2,

    /**
     * E5-small-v2 model (quantized).
     * More details: https://huggingface.co/intfloat/e5-small-v2
     */
    E5_SMALL_V2_Q
}
