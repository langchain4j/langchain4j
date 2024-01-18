package dev.langchain4j.store.embedding.vearch.api;

import dev.langchain4j.store.embedding.vearch.api.space.MetricType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class SearchRequest {

    private QueryParam query;
    private RetrievalParam retrievalParam;
    private Integer size;
    private String dbName;
    private String spaceName;

    @Getter
    @Setter
    @Builder
    public static class QueryParam {

        private List<VectorParam> vector;
    }

    @Getter
    @Setter
    @Builder
    public static class RetrievalParam {

        private MetricType metricType;
    }

    @Getter
    @Setter
    @Builder
    public static class VectorParam {

        private String field;
        private List<Float> feature;
    }
}
