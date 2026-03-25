package dev.langchain4j.store.embedding.pgvector;

import java.util.Collections;
import java.util.List;

/**
 * Metadata configuration implementation
 */
public class DefaultMetadataStorageConfig implements MetadataStorageConfig {
    private MetadataStorageMode storageMode;
    private List<String> columnDefinitions;
    private List<String> indexes;
    private String indexType;
    private List<List<String>> compoundIndexes;

    /**
     * Just for warnings ?
     */
    @SuppressWarnings("unused")
    public DefaultMetadataStorageConfig() {
        // Just for javadoc warning ?
    }

    public DefaultMetadataStorageConfig(MetadataStorageMode storageMode, List<String> columnDefinitions, List<String> indexes, String indexType, List<List<String>> compoundIndexes) {
        this.storageMode = storageMode;
        this.columnDefinitions = columnDefinitions;
        this.indexes = indexes;
        this.indexType = indexType;
        this.compoundIndexes = compoundIndexes;
    }

    /**
     * Default configuration
     *
     * @return Default configuration
     */
    public static MetadataStorageConfig defaultConfig() {
        return DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON NULL"))
                .build();
    }

    public static DefaultMetadataStorageConfigBuilder builder() {
        return new DefaultMetadataStorageConfigBuilder();
    }

    public MetadataStorageMode storageMode() {
        return this.storageMode;
    }

    public List<String> columnDefinitions() {
        return this.columnDefinitions;
    }

    public List<String> indexes() {
        return this.indexes;
    }

    public String indexType() {
        return this.indexType;
    }

    public List<List<String>> compoundIndexes() {
        return this.compoundIndexes != null ? this.compoundIndexes : Collections.emptyList();
    }

    public static class DefaultMetadataStorageConfigBuilder {
        private MetadataStorageMode storageMode;
        private List<String> columnDefinitions;
        private List<String> indexes;
        private String indexType;
        private List<List<String>> compoundIndexes;

        DefaultMetadataStorageConfigBuilder() {
        }

        public DefaultMetadataStorageConfigBuilder storageMode(MetadataStorageMode storageMode) {
            this.storageMode = storageMode;
            return this;
        }

        public DefaultMetadataStorageConfigBuilder columnDefinitions(List<String> columnDefinitions) {
            this.columnDefinitions = columnDefinitions;
            return this;
        }

        public DefaultMetadataStorageConfigBuilder indexes(List<String> indexes) {
            this.indexes = indexes;
            return this;
        }

        public DefaultMetadataStorageConfigBuilder indexType(String indexType) {
            this.indexType = indexType;
            return this;
        }

        public DefaultMetadataStorageConfigBuilder compoundIndexes(List<List<String>> compoundIndexes) {
            this.compoundIndexes = compoundIndexes;
            return this;
        }

        public DefaultMetadataStorageConfig build() {
            return new DefaultMetadataStorageConfig(this.storageMode, this.columnDefinitions, this.indexes, this.indexType, this.compoundIndexes);
        }

        public String toString() {
            return "DefaultMetadataStorageConfig.DefaultMetadataStorageConfigBuilder(storageMode=" + this.storageMode + ", columnDefinitions=" + this.columnDefinitions + ", indexes=" + this.indexes + ", indexType=" + this.indexType + ", compoundIndexes=" + this.compoundIndexes + ")";
        }
    }
}
