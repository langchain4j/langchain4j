package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.store.embedding.vearch.index.search.SearchIndexParam;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class SearchRequest {

    private String dbName;
    private String spaceName;
    private List<Vector> vectors;
    private List<String> fields;
    private Boolean vectorValue;
    private Integer limit;
    private SearchIndexParam indexParams;

    SearchRequest() {
    }

    SearchRequest(Builder builder) {
        this.dbName = builder.dbName;
        this.spaceName = builder.spaceName;
        this.vectors = builder.vectors;
        this.fields = builder.fields;
        this.vectorValue = builder.vectorValue;
        this.limit = builder.limit;
        this.indexParams = builder.indexParams;
    }

    public String getDbName() {
        return dbName;
    }

    public String getSpaceName() {
        return spaceName;
    }

    public List<Vector> getVectors() {
        return vectors;
    }

    public List<String> getFields() {
        return fields;
    }

    public Boolean getVectorValue() {
        return vectorValue;
    }

    public Integer getLimit() {
        return limit;
    }

    public SearchIndexParam getIndexParams() {
        return indexParams;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private String dbName;
        private String spaceName;
        private List<Vector> vectors;
        private List<String> fields;
        private Boolean vectorValue;
        private Integer limit;
        private SearchIndexParam indexParams;

        Builder dbName(String dbName) {
            this.dbName = dbName;
            return this;
        }

        Builder spaceName(String spaceName) {
            this.spaceName = spaceName;
            return this;
        }

        Builder vectors(List<Vector> vectors) {
            this.vectors = vectors;
            return this;
        }

        Builder fields(List<String> fields) {
            this.fields = fields;
            return this;
        }

        Builder vectorValue(Boolean vectorValue) {
            this.vectorValue = vectorValue;
            return this;
        }

        Builder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        Builder indexParams(SearchIndexParam indexParams) {
            this.indexParams = indexParams;
            return this;
        }

        SearchRequest build() {
            return new SearchRequest(this);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    static class Vector {

        private String field;
        private List<Float> feature;
        private Double minScore;

        Vector() {
        }

        Vector(String field, List<Float> feature, Double minScore) {
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

            Vector build() {
                return new Vector(field, feature, minScore);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    static class RankerParam {

        private String type;
        private List<Double> params;

        RankerParam() {
        }

        RankerParam(String type, List<Double> params) {
            this.type = type;
            this.params = params;
        }

        public String getType() {
            return type;
        }

        public List<Double> getParams() {
            return params;
        }

        static Builder builder() {
            return new Builder();
        }

        static class Builder {

            private String type;
            private List<Double> params;

            Builder type(String type) {
                this.type = type;
                return this;
            }

            Builder params(List<Double> params) {
                this.params = params;
                return this;
            }

            RankerParam build() {
                return new RankerParam(type, params);
            }
        }
    }
}
