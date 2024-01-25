package dev.langchain4j.store.embedding.vearch;

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
     * This attribute's key set should contain
     * {@link VearchConfig#embeddingFieldName}, {@link VearchConfig#textFieldName} and {@link VearchConfig#metadataFieldNames}
     */
    private Map<String, SpacePropertyParam> properties;
    @Builder.Default
    private String embeddingFieldName = "embedding";
    @Builder.Default
    private String textFieldName = "text";
    private List<ModelParam> modelParams;
    /**
     * This attribute should be the subset of {@link VearchConfig#properties}'s key set
     */
    private List<String> metadataFieldNames;

    public static VearchConfig getDefaultConfig() {
        // init properties
        Map<String, SpacePropertyParam> properties = new HashMap<>(4);
        properties.put("embedding", SpacePropertyParam.VectorParam.builder()
                .index(true)
                .storeType(SpaceStoreType.MEMORY_ONLY)
                .dimension(384)
                .build());
        properties.put("text", SpacePropertyParam.StringParam.builder().build());

        return VearchConfig.builder()
                .spaceEngine(SpaceEngine.builder()
                        .name("gamma")
                        .indexSize(1L)
                        .retrievalType(RetrievalType.FLAT)
                        .retrievalParam(RetrievalParam.FLAT.builder()
                                .build())
                        .build())
                .properties(properties)
                .databaseName("embedding_db")
                .spaceName("embedding_space")
                .modelParams(singletonList(ModelParam.builder()
                        .modelId("vgg16")
                        .fields(singletonList("string"))
                        .out("feature")
                        .build()))
                .build();
    }
}
