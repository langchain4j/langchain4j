package dev.langchain4j.store.embedding.vearch;

import dev.langchain4j.store.embedding.vearch.api.space.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;

@Getter
@Setter
@Builder
public class VearchConfig {

    private String databaseName;
    private String spaceName;
    private SpaceEngine spaceEngine;
    /**
     * This attribute's key set should contain {@link VearchConfig#metadataFieldNames}
     */
    private Map<String, SpacePropertyParam> properties;
    private String embeddingFieldName;
    private String textFieldName;
    private List<ModelParam> modelParams;
    /**
     * This attribute should be the subset of {@link VearchConfig#properties}'s key set
     */
    private List<String> metadataFieldNames;

    public static VearchConfig getDefaultConfig() {
        String embeddingFieldName = "text_embedding";
        String textFieldName = "text";
        String metadataFieldName = "metadata";

        // init properties
        Map<String, SpacePropertyParam> properties = new HashMap<>(4);
        properties.put(embeddingFieldName, SpacePropertyParam.VectorParam.builder()
                .index(true)
                .storeType(SpaceStoreType.MEMORY_ONLY)
                .dimension(384)
                .build());
        properties.put(textFieldName, SpacePropertyParam.StringParam.builder().build());
        // metadata
        properties.put(metadataFieldName, SpacePropertyParam.StringParam.builder().build());

        return VearchConfig.builder()
                .spaceEngine(SpaceEngine.builder()
                        .name("gamma")
                        .indexSize(1L)
                        .retrievalType(RetrievalType.FLAT)
                        .retrievalParam(RetrievalParam.HNSWParam.builder()
                                .metricType(MetricType.L2)
                                .nlinks(-1)
                                .efConstruction(-1)
                                .build())
                        .build())
                .properties(properties)
                .embeddingFieldName(embeddingFieldName)
                .textFieldName(textFieldName)
                .databaseName("embedding_db")
                .spaceName("embedding_space")
                .modelParams(singletonList(ModelParam.builder()
                        .modelId("vgg16")
                        .fields(singletonList("string"))
                        .out("feature")
                        .build()))
                .metadataFieldNames(singletonList(metadataFieldName))
                .build();
    }
}
