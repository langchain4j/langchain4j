package dev.langchain4j.model.inprocess;

/**
 * Lists all the currently supported in-process embedding models.
 * New models will be added gradually.
 * If you would like a new model to be added, please open a GitHub issue at: https://github.com/langchain4j/langchain4j/issues/new/choose
 */
public enum InProcessEmbeddingModelType {

    /**
     * Model: SentenceTransformers all-MiniLM-L6-v2
     * Max tokens: 256 (according to https://www.sbert.net/docs/pretrained_models.html), but on practice up to 510 works (however, it is unclear whether there is a degradation in quality)
     * Dimensions: 384
     * Required dependency: langchain4j-embeddings-all-minilm-l6-v2
     * More details:
     * https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2
     * https://www.sbert.net/docs/pretrained_models.html
     */
    ALL_MINILM_L6_V2,

    /**
     * Model: SentenceTransformers all-MiniLM-L6-v2 quantized (smaller and faster, but provides slightly inferior results)
     * Max tokens: 256 (according to https://www.sbert.net/docs/pretrained_models.html), but on practice up to 510 works (however, it is unclear whether there is a degradation in quality)
     * Dimensions: 384
     * Required dependency: langchain4j-embeddings-all-minilm-l6-v2-q
     * More details:
     * https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2
     * https://www.sbert.net/docs/pretrained_models.html
     */
    ALL_MINILM_L6_V2_Q,

    /**
     * Model: Microsoft E5-small-v2
     * Max tokens: 510
     * Dimensions: 384
     * Required dependency: langchain4j-embeddings-e5-small-v2
     * It is recommended to use "query: " and "passage: " prefixes.
     * More details: https://huggingface.co/intfloat/e5-small-v2
     */
    E5_SMALL_V2,

    /**
     * Model: Microsoft E5-small-v2 quantized (smaller and faster, but provides slightly inferior results)
     * Max tokens: 510
     * Dimensions: 384
     * It is recommended to use "query: " and "passage: " prefixes.
     * Required dependency: langchain4j-embeddings-e5-small-v2-q
     * More details: https://huggingface.co/intfloat/e5-small-v2
     */
    E5_SMALL_V2_Q
}
