package dev.langchain4j.store.embedding.vearch;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
class SearchRequest {

    private QueryParam query;
    private Integer size;
    private List<String> fields;

    @Getter
    @Setter
    @Builder
    public static class QueryParam {

        private List<VectorParam> sum;
    }

    @Getter
    @Setter
    @Builder
    public static class VectorParam {

        private String field;
        private List<Float> feature;
        private Double minScore;
    }
}
