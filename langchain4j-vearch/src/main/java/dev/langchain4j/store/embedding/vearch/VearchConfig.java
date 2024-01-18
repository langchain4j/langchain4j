package dev.langchain4j.store.embedding.vearch;

import dev.langchain4j.store.embedding.vearch.api.space.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Builder
public class VearchConfig {

    private String databaseName;
    private String spaceName;
    private SpaceEngine spaceEngine;
    private Map<String, SpacePropertyParam> properties;
    private String embeddingFieldName;
    private String textFieldName;
    private MetricType metricType;

    public static VearchConfig getDefaultConfig() {
        Map<String, SpacePropertyParam> properties = new HashMap<>(4);
        properties.put("embedding", SpacePropertyParam.VectorParam.builder().build());
        properties.put("text", SpacePropertyParam.KeywordParam.builder().build());

        return VearchConfig.builder()
                .spaceEngine(SpaceEngine.builder()
                        .retrievalType(RetrievalType.HNSW)
                        .retrievalParam(RetrievalParam.HNSWParam.builder()
                                .metricType(MetricType.L2)
                                .nlinks(-1)
                                .efConstruction(-1)
                                .build())
                        .build())
                .properties(properties)
                .embeddingFieldName("embedding")
                .textFieldName("text")
                .metricType(MetricType.L2)
                .databaseName("embedding_db")
                .spaceName("embedding_space")
                .build();
    }
}
