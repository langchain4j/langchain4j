package dev.langchain4j.store.embedding.vearch.api;

import dev.langchain4j.store.embedding.vearch.api.space.MetricType;

import java.util.List;

public class SearchRequest {

    private QueryParam query;
    private RetrievalParam retrievalParam;
    private Integer size;
    private String dbName;
    private String spaceName;

    public static class QueryParam {

        private List<VectorParam> vector;

        public List<VectorParam> getVector() {
            return vector;
        }

        public void setVector(List<VectorParam> vector) {
            this.vector = vector;
        }
    }

    public static class RetrievalParam {

        private MetricType metricType;

        public MetricType getMetricType() {
            return metricType;
        }

        public void setMetricType(MetricType metricType) {
            this.metricType = metricType;
        }
    }

    public static class VectorParam {

        private String field;
        private List<Float> feature;

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public List<Float> getFeature() {
            return feature;
        }

        public void setFeature(List<Float> feature) {
            this.feature = feature;
        }
    }

    public QueryParam getQuery() {
        return query;
    }

    public void setQuery(QueryParam query) {
        this.query = query;
    }

    public RetrievalParam getRetrievalParam() {
        return retrievalParam;
    }

    public void setRetrievalParam(RetrievalParam retrievalParam) {
        this.retrievalParam = retrievalParam;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getSpaceName() {
        return spaceName;
    }

    public void setSpaceName(String spaceName) {
        this.spaceName = spaceName;
    }
}
