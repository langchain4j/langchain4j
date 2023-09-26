package dev.langchain4j.store.embedding.redis;

/**
 * Similarity metric used by Redis
 */
enum MetricType {

    /**
     * cosine similarity
     */
    COSINE,

    /**
     * inner product
     */
    IP,

    /**
     * euclidean distance
     */
    L2
}
