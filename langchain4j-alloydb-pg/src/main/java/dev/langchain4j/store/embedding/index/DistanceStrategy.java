package dev.langchain4j.store.embedding.index;

/** Distance strategy for vector  */
public enum DistanceStrategy {
    /**
     * Euclidean distance options
     */
    EUCLIDEAN("<->", "l2_distance", "vector_l2_ops", "l2"),
    /** Cosine distance options */
    COSINE_DISTANCE("<=>", "cosine_distance", "vector_cosine_ops", "cosine"),
    /** Inner product startegy options */
    INNER_PRODUCT("<#>", "inner_product", "vector_ip_ops", "dot_product");

    private final String operator;
    private final String searchFunction;
    private final String indexFunction;
    private final String scannIndexFunction;

    /**
     * Constructor for DistanceStrategy
     */
    private DistanceStrategy(String operator, String searchFunction, String indexFunction, String scannIndexFunction) {
        this.indexFunction = indexFunction;
        this.operator = operator;
        this.scannIndexFunction = scannIndexFunction;
        this.searchFunction = searchFunction;
    }
    /**
     * get operator
     * @return DistanceStrategy's operator
     */
    public String getOperator() {
        return operator;
    }
    /**
     * search function
     * @return DistanceStrategy's search function
     */
    public String getSearchFunction() {
        return searchFunction;
    }
    /**
     * get index function
     * @return DistanceStrategy's index function
     */
    public String getIndexFunction() {
        return indexFunction;
    }
    /**
     * get ScaNN index function
     * @return DistanceStrategy's ScaNN index function
     */
    public String getScannIndexFunction() {
        return scannIndexFunction;
    }
}
