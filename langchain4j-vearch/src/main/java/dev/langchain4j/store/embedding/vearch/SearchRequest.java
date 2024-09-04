package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Getter
@Setter
@Builder
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

    @Getter
    @Setter
    @Builder
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
    }

    @Getter
    @Setter
    @Builder
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
    }
}
