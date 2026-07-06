package dev.langchain4j.store.embedding.oracle.vecdb;

/**
 * Distance metrics supported by {@code DBMS_VECTOR_DATABASE.CREATE_INDEX}.
 */
public enum VecDbDistanceMetric {
    COSINE,
    MANHATTAN,
    HAMMING,
    JACCARD,
    DOT,
    EUCLIDEAN,
    L2_SQUARED,
    EUCLIDEAN_SQUARED
}
