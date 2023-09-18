package dev.langchain4j.store.embedding.redis;

/**
 * Redis vector field distance Metric
 */
public enum MetricType {

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
