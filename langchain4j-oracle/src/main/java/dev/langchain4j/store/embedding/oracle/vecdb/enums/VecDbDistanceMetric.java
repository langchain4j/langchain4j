package dev.langchain4j.store.embedding.oracle.vecdb.enums;

/**
 * Distance metrics supported by this store's FLOAT32 VecDB vector indexes.
 */
public enum VecDbDistanceMetric {
    COSINE,
    MANHATTAN,
    DOT,
    EUCLIDEAN,
    L2_SQUARED,
    EUCLIDEAN_SQUARED
}
