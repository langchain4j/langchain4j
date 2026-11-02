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
     * The INNER_PRODUCT distance function.
     * <p>
     * The score of a match is {@code (1 + inner product) / 2}. Most embedding models return normalized embeddings
     * (with a length of {@code 1}). For those, this is the same score as with {@link #COSINE}.
     * <p>
     * For embeddings that are not normalized, the inner product can be above {@code 1} or below {@code -1}.
     * The score is then limited to {@code 1} or {@code 0}, so several matches can have the same score.
     * The matches are still ordered by their inner product.
     */
    INNER_PRODUCT,
    /**
     * The NEGATIVE_INNER_PRODUCT distance function.
     * <p>
     * The score of a match is computed from the inner product in the same way as for {@link #INNER_PRODUCT}.
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
