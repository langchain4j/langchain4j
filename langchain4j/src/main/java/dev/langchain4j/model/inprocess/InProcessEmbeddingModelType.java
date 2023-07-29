package dev.langchain4j.model.inprocess;

/**
 * Lists all the currently supported in-process embedding models.
 * New models will be added gradually.
 * If you would like a new model to be added, please open a GitHub issue at: https://github.com/langchain4j/langchain4j/issues/new/choose
 */
public enum InProcessEmbeddingModelType {

    /**
     * SentenceTransformers all-MiniLM-L6-v2 model.
     * Requires langchain4j-embeddings-all-minilm-l6-v2 dependency.
     * Max tokens: 510
     * Dimensions: 384
     * More details: https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2
     */
    ALL_MINILM_L6_V2,

    /**
     * SentenceTransformers all-MiniLM-L6-v2 model (quantized).
     * Requires langchain4j-embeddings-all-minilm-l6-v2-q dependency.
     * Max tokens: 510
     * Dimensions: 384
     * More details: https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2
     */
    ALL_MINILM_L6_V2_Q,

    /**
     * E5-small-v2 model.
     * Requires langchain4j-embeddings-e5-small-v2 dependency.
     * Max tokens: 510
     * Dimensions: 384
     * It is recommended to use "query: " and "passage: " prefixes.
     * More details: https://huggingface.co/intfloat/e5-small-v2
     */
    E5_SMALL_V2,

    /**
     * E5-small-v2 model (quantized).
     * Requires langchain4j-embeddings-e5-small-v2-q dependency.
     * Max tokens: 510
     * Dimensions: 384
     * It is recommended to use "query: " and "passage: " prefixes.
     * More details: https://huggingface.co/intfloat/e5-small-v2
     */
    E5_SMALL_V2_Q
}
