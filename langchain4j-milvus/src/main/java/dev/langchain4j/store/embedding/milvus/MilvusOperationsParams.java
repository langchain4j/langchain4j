package dev.langchain4j.store.embedding.milvus;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class MilvusOperationsParams {

    /**
     * Possible options: STRONG, BOUNDED or EVENTUALLY.
     */
    private final ConsistencyLevel consistencyLevel;

    /**
     * Possible options: L2 or IP.
     * Metric types for binary vectors are not supported at the moment
     */
    private final MetricType metricType;

    /**
     * During a similarity search in Milvus, the vector value is not returned.
     * To retrieve the value from a vector field, an additional query is required.
     * Enabling this query may impact the performance of the search.
     * The default value is set to 'false'.
     */
    private final boolean queryForVectorOnSearch;

    public MilvusOperationsParams(ConsistencyLevel consistencyLevel, MetricType metricType, boolean queryForVectorOnSearch) {
        this.consistencyLevel = ensureNotNull(consistencyLevel, "consistencyLevel");
        this.metricType = ensureNotNull(metricType, "metricType");
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
