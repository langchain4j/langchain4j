package dev.langchain4j.store.embedding.oracle;

/**
 * Enumeration of <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/vector-distance-metrics.html">
 * distance metrics supported by Oracle Database
 * </a>.
 */
public enum DistanceMetric {

    /**
     * <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/cosine-similarity.html>
     * The cosine distance between two vectors
     * </a>. This is the default used by Oracle Database.
     */
    COSINE,

    /**
     * <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/dot-product-similarity.html">
     * The negated dot product of two vectors
     * </a>. This metric is also referred to as the "inner product".
     */
    DOT,

    /**
     * <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/euclidean-and-squared-euclidean-distances.html">
     * The Euclidean distance between two vectors
     * </a>. This metric is also referred to as the "L2 distance".
     */
    EUCLIDEAN,

    /**
     * <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/euclidean-and-squared-euclidean-distances.html">
     * The Euclidean distance between two vectors, without taking the square root
     * </a>. This metric is also referred to as the "L2 squared distance".
     */
    EUCLIDEAN_SQUARED,

    /**
     * <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/hamming-similarity.html">
     * The hamming distance between two vectors
     * </a>. No vector index will be created if {@link OracleEmbeddingStore} is configured with this option. In 23.4,
     * Oracle Database only support
     * having this distance metric are not
     */
    HAMMING,

    /**
     * <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/manhattan-distance.html">
     * The Manhattan distance between two vectors
     * </a>. This metric is also referred to as the "L1 distance" or "taxicab distance".
     */
    MANHATTAN;

}
