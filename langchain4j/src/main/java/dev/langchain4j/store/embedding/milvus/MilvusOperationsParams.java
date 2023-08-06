package dev.langchain4j.store.embedding.milvus;

public class MilvusOperationsParams {

    /**
     * Possible options: STRONG, BOUNDED or EVENTUALLY.
     */
    private String consistencyLevel;

    /**
     * Possible options: L2 or IP.
     * Metric types for binary vectors are not supported at he moment
     */
    private String metricType;

    /**
     * During a similarity search in Milvus, the vector value is not returned.
     * To retrieve the value from a vector field, an additional query is required.
     * Enabling this query may impact the performance of the search.
     * The default value is set to 'false'.
     */
    private boolean queryForVectorOnSearch;

    public MilvusOperationsParams(String consistencyLevel, String metricType, boolean queryForVectorOnSearch) {
        if (consistencyLevelIsValid(consistencyLevel)) {
            this.consistencyLevel = consistencyLevel;
        }

        if (metricTypeIsValid(metricType)) {
            this.metricType = metricType;
        }

        this.queryForVectorOnSearch = queryForVectorOnSearch;
    }

    public String consistencyLevel() {
        return consistencyLevel;
    }

    public String metricType() {
        return metricType;
    }

    public boolean queryForVectorOnSearch() {
        return queryForVectorOnSearch;
    }

    private boolean consistencyLevelIsValid(String input) {
        if (!"STRONG".equals(input) && !"BOUNDED".equals(input) && !"EVENTUALLY".equals(input)) {
            throw new IllegalArgumentException("Invalid consistencyLevel. Acceptable values are: STRONG, BOUNDED, or EVENTUALLY.");
        }
        return true;
    }

    private boolean metricTypeIsValid(String input) {
        if (!"L2".equals(input) && !"IP".equals(input)) {
            throw new IllegalArgumentException("Invalid metricType. Acceptable values are: L2 or IP.");
        }
        return true;
    }
}
