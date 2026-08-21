package dev.langchain4j.store.embedding.hibernate;

/**
 * Distance function
 * <ul>
 * <li>COSINE: The COSINE distance function
 * <li>EUCLIDEAN: The EUCLIDEAN or L2 distance function
 * <li>EUCLIDEAN_SQUARED: The squared EUCLIDEAN distance function
 * <li>MANHATTAN: The MANHATTAN, TAXICAB or L1 distance function
 * <li>INNER_PRODUCT: The INNER_PRODUCT distance function
 * <li>NEGATIVE_INNER_PRODUCT: The NEGATIVE_INNER_PRODUCT distance function
 * <li>HAMMING: The HAMMING distance function
 * <li>JACCARD: The JACCARD distance function
 * </ul>
 * <p>
 * Default value: COSINE
 */
public enum DistanceFunction {
    /**
     * The COSINE distance function
     */
    COSINE,
    /**
     * The EUCLIDEAN or L2 distance function
     */
    EUCLIDEAN,
    /**
     * The squared EUCLIDEAN distance function
     */
    EUCLIDEAN_SQUARED,
    /**
     * The MANHATTAN, TAXICAB or L1 distance function
     */
    MANHATTAN,
    /**
     * The INNER_PRODUCT distance function
     */
    INNER_PRODUCT,
    /**
     * The NEGATIVE_INNER_PRODUCT distance function
     */
    NEGATIVE_INNER_PRODUCT,
    /**
     * The HAMMING distance function
     */
    HAMMING,
    /**
     * The JACCARD distance function
     */
    JACCARD
}
