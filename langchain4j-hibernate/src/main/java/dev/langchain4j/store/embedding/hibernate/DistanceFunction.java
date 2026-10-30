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
     * Unlike the other distance functions, the inner product is unbounded, so it cannot be mapped onto the
     * {@code 0..1} relevance score that a search result reports in a linear way. A sigmoid function is used instead,
     * which means a score of exactly {@code 1} is never reached: searching with a minimum score of {@code 1} always
     * returns no results. Use a value below {@code 1}, for example {@code 0.9}.
     */
    INNER_PRODUCT,
    /**
     * The NEGATIVE_INNER_PRODUCT distance function.
     * <p>
     * The same score mapping as for {@link #INNER_PRODUCT} applies, so a minimum score of exactly {@code 1} also
     * returns no results here.
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
