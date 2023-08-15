package dev.langchain4j.store.embedding.milvus;

public class MilvusOperationsParams {

    /**
     * Possible options: STRONG, BOUNDED or EVENTUALLY.
     */
    private ConsistencyLevel consistencyLevel;

    /**
     * Possible options: L2 or IP.
     * Metric types for binary vectors are not supported at the moment
     */
    private MetricType metricType;

    /**
     * During a similarity search in Milvus, the vector value is not returned.
     * To retrieve the value from a vector field, an additional query is required.
     * Enabling this query may impact the performance of the search.
     * The default value is set to 'false'.
     */
    private boolean queryForVectorOnSearch;

    public MilvusOperationsParams(ConsistencyLevel consistencyLevel, MetricType metricType, boolean queryForVectorOnSearch) {
        this.consistencyLevel = consistencyLevel;
        this.metricType = metricType;
        this.queryForVectorOnSearch = queryForVectorOnSearch;
    }

    public ConsistencyLevel consistencyLevel() {
        return consistencyLevel;
    }

    public MetricType metricType() {
        return metricType;
    }

    public boolean queryForVectorOnSearch() {
        return queryForVectorOnSearch;
    }

}
