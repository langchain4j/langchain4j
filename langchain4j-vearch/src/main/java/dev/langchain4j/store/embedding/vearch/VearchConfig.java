package dev.langchain4j.store.embedding.vearch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;

public class VearchConfig {

    private String databaseName;
    private String spaceName;
    private SpaceEngine spaceEngine;
    /**
     * This attribute's key set should contain
     * {@link VearchConfig#embeddingFieldName}, {@link VearchConfig#textFieldName} and {@link VearchConfig#metadataFieldNames}
     */
    private Map<String, SpacePropertyParam> properties;
    private String embeddingFieldName;
    private String textFieldName;
    private List<ModelParam> modelParams;
    /**
     * This attribute should be the subset of {@link VearchConfig#properties}'s key set
     */
    private List<String> metadataFieldNames;

    public VearchConfig() {
    }

    public VearchConfig(String databaseName, String spaceName, SpaceEngine spaceEngine, Map<String, SpacePropertyParam> properties, String embeddingFieldName, String textFieldName, List<ModelParam> modelParams, List<String> metadataFieldNames) {
        this.databaseName = databaseName;
        this.spaceName = spaceName;
        this.spaceEngine = spaceEngine;
        this.properties = properties;
        this.embeddingFieldName = embeddingFieldName;
        this.textFieldName = textFieldName;
        this.modelParams = modelParams;
        this.metadataFieldNames = metadataFieldNames;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getSpaceName() {
        return spaceName;
    }

    public void setSpaceName(String spaceName) {
        this.spaceName = spaceName;
    }

    public SpaceEngine getSpaceEngine() {
        return spaceEngine;
    }

    public void setSpaceEngine(SpaceEngine spaceEngine) {
        this.spaceEngine = spaceEngine;
    }

    public Map<String, SpacePropertyParam> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, SpacePropertyParam> properties) {
        this.properties = properties;
    }

    public String getEmbeddingFieldName() {
        return embeddingFieldName;
    }

    public void setEmbeddingFieldName(String embeddingFieldName) {
        this.embeddingFieldName = embeddingFieldName;
    }

    public String getTextFieldName() {
        return textFieldName;
    }

    public void setTextFieldName(String textFieldName) {
        this.textFieldName = textFieldName;
    }

    public List<ModelParam> getModelParams() {
        return modelParams;
    }

    public void setModelParams(List<ModelParam> modelParams) {
        this.modelParams = modelParams;
    }

    public List<String> getMetadataFieldNames() {
        return metadataFieldNames;
    }

    public void setMetadataFieldNames(List<String> metadataFieldNames) {
        this.metadataFieldNames = metadataFieldNames;
    }

    public static VearchConfigBuilder builder() {
        return new VearchConfigBuilder();
    }

    public static class VearchConfigBuilder {

        private String databaseName;
        private String spaceName;
        private SpaceEngine spaceEngine;
        private Map<String, SpacePropertyParam> properties;
        private String embeddingFieldName = "embedding";
        private String textFieldName = "text";
        private List<ModelParam> modelParams;
        private List<String> metadataFieldNames;

        public VearchConfigBuilder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public VearchConfigBuilder spaceName(String spaceName) {
            this.spaceName = spaceName;
            return this;
        }

        public VearchConfigBuilder spaceEngine(SpaceEngine spaceEngine) {
            this.spaceEngine = spaceEngine;
            return this;
        }

        public VearchConfigBuilder properties(Map<String, SpacePropertyParam> properties) {
            this.properties = properties;
            return this;
        }

        public VearchConfigBuilder embeddingFieldName(String embeddingFieldName) {
            this.embeddingFieldName = embeddingFieldName;
            return this;
        }

        public VearchConfigBuilder textFieldName(String textFieldName) {
            this.textFieldName = textFieldName;
            return this;
        }

        public VearchConfigBuilder modelParams(List<ModelParam> modelParams) {
            this.modelParams = modelParams;
            return this;
        }

        public VearchConfigBuilder metadataFieldNames(List<String> metadataFieldNames) {
            this.metadataFieldNames = metadataFieldNames;
            return this;
        }

        public VearchConfig build() {
            return new VearchConfig(databaseName, spaceName, spaceEngine, properties, embeddingFieldName, textFieldName, modelParams, metadataFieldNames);
        }
    }

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
