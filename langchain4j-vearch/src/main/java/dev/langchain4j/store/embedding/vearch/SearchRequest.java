package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class SearchRequest {

    private QueryParam query;
    private Integer size;
    private List<String> fields;

    SearchRequest() {
    }

    SearchRequest(QueryParam query, Integer size, List<String> fields) {
        this.query = query;
        this.size = size;
        this.fields = fields;
    }

    public QueryParam getQuery() {
        return query;
    }

    public Integer getSize() {
        return size;
    }

    public List<String> getFields() {
        return fields;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private QueryParam query;
        private Integer size;
        private List<String> fields;

        Builder query(QueryParam query) {
            this.query = query;
            return this;
        }

        Builder size(Integer size) {
            this.size = size;
            return this;
        }

        Builder fields(List<String> fields) {
            this.fields = fields;
            return this;
        }

        SearchRequest build() {
            return new SearchRequest(query, size, fields);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    static class QueryParam {

        private List<VectorParam> sum;

        QueryParam() {
        }

        QueryParam(List<VectorParam> sum) {
            this.sum = sum;
        }

        public List<VectorParam> getSum() {
            return sum;
        }

        static Builder builder() {
            return new Builder();
        }

        static class Builder {

            private List<VectorParam> sum;

            Builder sum(List<VectorParam> sum) {
                this.sum = sum;
                return this;
            }

            QueryParam build() {
                return new QueryParam(sum);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    static class VectorParam {

        private String field;
        private List<Float> feature;
        private Double minScore;

        VectorParam() {
        }

        VectorParam(String field, List<Float> feature, Double minScore) {
            this.field = field;
            this.feature = feature;
            this.minScore = minScore;
        }

        public String getField() {
            return field;
        }

        public List<Float> getFeature() {
            return feature;
        }

        public Double getMinScore() {
            return minScore;
        }

        static Builder builder() {
            return new Builder();
        }

        static class Builder {

            private String field;
            private List<Float> feature;
            private Double minScore;

            Builder field(String field) {
                this.field = field;
                return this;
            }

            Builder feature(List<Float> feature) {
                this.feature = feature;
                return this;
            }

            Builder minScore(Double minScore) {
                this.minScore = minScore;
                return this;
            }

            VectorParam build() {
                return new VectorParam(field, feature, minScore);
            }
        }
    }
}
