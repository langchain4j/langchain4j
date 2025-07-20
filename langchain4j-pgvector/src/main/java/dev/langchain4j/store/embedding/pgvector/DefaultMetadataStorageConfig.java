package dev.langchain4j.store.embedding.pgvector;

import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.List;

/**
 * Metadata configuration implementation
 */
@Accessors(fluent = true)
public class DefaultMetadataStorageConfig implements MetadataStorageConfig {
    private MetadataStorageMode storageMode;
    private List<String> columnDefinitions;
    private List<String> indexes;
    private String indexType;

    /**
     * Just for warnings ?
     */
    @SuppressWarnings("unused")
    public DefaultMetadataStorageConfig() {
        // Just for javadoc warning ?
    }

    public DefaultMetadataStorageConfig(MetadataStorageMode storageMode, List<String> columnDefinitions, List<String> indexes, String indexType) {
        this.storageMode = storageMode;
        this.columnDefinitions = columnDefinitions;
        this.indexes = indexes;
        this.indexType = indexType;
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

    public static class DefaultMetadataStorageConfigBuilder {
        private MetadataStorageMode storageMode;
        private List<String> columnDefinitions;
        private List<String> indexes;
        private String indexType;

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

        public DefaultMetadataStorageConfig build() {
            return new DefaultMetadataStorageConfig(this.storageMode, this.columnDefinitions, this.indexes, this.indexType);
        }

        public String toString() {
            return "DefaultMetadataStorageConfig.DefaultMetadataStorageConfigBuilder(storageMode=" + this.storageMode + ", columnDefinitions=" + this.columnDefinitions + ", indexes=" + this.indexes + ", indexType=" + this.indexType + ")";
        }
    }
}
