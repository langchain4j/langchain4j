package dev.langchain4j.model.inprocess;

/**
 * Lists all the pre-packaged in-process embedding models currently available.
 * New models will be added gradually.
 * You can also use any custom embedding model. See {@link InProcessEmbeddingModel} and {@link InProcessEmbeddingModel#InProcessEmbeddingModel(String)} for details.
 * If you would like a new model to be added, please open a GitHub issue <a href="https://github.com/langchain4j/langchain4j/issues/new/choose">here</a>.
 */
public enum InProcessEmbeddingModelType {

    /**
     * Model: SentenceTransformers all-MiniLM-L6-v2
     * <p>
     * Max tokens: 256 (according to <a href="https://www.sbert.net/docs/pretrained_models.html">sbert</a>), but on practice up to 510 works (however, it is unclear whether there is a degradation in quality)
     * <p>
     * Dimensions: 384
     * <p>
     * More details
     * <a href="https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2">here</a> and
     * <a href="https://www.sbert.net/docs/pretrained_models.html">here</a>
     * <p>
     * Required dependency: langchain4j-embeddings-all-minilm-l6-v2
     */
    ALL_MINILM_L6_V2,

    /**
     * Model: SentenceTransformers all-MiniLM-L6-v2 quantized (smaller and faster, but provides slightly inferior results)
     * <p>
     * Max tokens: 256 (according to <a href="https://www.sbert.net/docs/pretrained_models.html">sbert</a>), but on practice up to 510 works (however, it is unclear whether there is a degradation in quality)
     * <p>
     * Dimensions: 384
     * <p>
     * More details
     * <a href="https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2">here</a> and
     * <a href="https://www.sbert.net/docs/pretrained_models.html">here</a>
     * <p>
     * Required dependency: langchain4j-embeddings-all-minilm-l6-v2-q
     */
    ALL_MINILM_L6_V2_Q,

    /**
     * Model: BAAI bge-small-en
     * <p>
     * Max tokens: 510
     * <p>
     * Dimensions: 384
     * <p>
     * It is recommended to add "Represent this sentence for searching relevant passages:" prefix to a query.
     * <p>
     * More details <a href="https://huggingface.co/BAAI/bge-small-en">here</a>
     * <p>
     * Required dependency: langchain4j-embeddings-bge-small-en
     */
    BGE_SMALL_EN,

    /**
     * Model: BAAI bge-small-en quantized (smaller and faster, but provides slightly inferior results)
     * <p>
     * Max tokens: 510
     * <p>
     * Dimensions: 384
     * <p>
     * It is recommended to add "Represent this sentence for searching relevant passages:" prefix to a query.
     * <p>
     * More details <a href="https://huggingface.co/BAAI/bge-small-en">here</a>
     * <p>
     * Required dependency: langchain4j-embeddings-bge-small-en-q
     */
    BGE_SMALL_EN_Q,

    /**
     * Model: BAAI bge-small-zh (Chinese language)
     * <p>
     * Max tokens: 510
     * <p>
     * Dimensions: 512
     * <p>
     * It is recommended to add "为这个句子生成表示以用于检索相关文章：" prefix to a query.
     * <p>
     * More details <a href="https://huggingface.co/BAAI/bge-small-zh">here</a>
     * <p>
     * Required dependency: langchain4j-embeddings-bge-small-zh
     */
    BGE_SMALL_ZH,

    /**
     * Model: BAAI bge-small-zh (Chinese language) quantized (smaller and faster, but provides slightly inferior results)
     * <p>
     * Max tokens: 510
     * <p>
     * Dimensions: 512
     * <p>
     * It is recommended to add "为这个句子生成表示以用于检索相关文章：" prefix to a query.
     * <p>
     * More details <a href="https://huggingface.co/BAAI/bge-small-zh">here</a>
     * <p>
     * Required dependency: langchain4j-embeddings-bge-small-zh-q
     */
    BGE_SMALL_ZH_Q,

    /**
     * Model: Microsoft E5-small-v2
     * <p>
     * Max tokens: 510
     * <p>
     * Dimensions: 384
     * <p>
     * It is recommended to use the "query:" prefix for queries and the "passage:" prefix for segments.
     * <p>
     * More details <a href="https://huggingface.co/intfloat/e5-small-v2">here</a>
     * <p>
     * Required dependency: langchain4j-embeddings-e5-small-v2
     */
    E5_SMALL_V2,

    /**
     * Model: Microsoft E5-small-v2 quantized (smaller and faster, but provides slightly inferior results)
     * <p>
     * Max tokens: 510
     * <p>
     * Dimensions: 384
     * <p>
     * It is recommended to use the "query:" prefix for queries and the "passage:" prefix for segments.
     * <p>
     * More details <a href="https://huggingface.co/intfloat/e5-small-v2">here</a>
     * <p>
     * Required dependency: langchain4j-embeddings-e5-small-v2-q
     */
    E5_SMALL_V2_Q
}
