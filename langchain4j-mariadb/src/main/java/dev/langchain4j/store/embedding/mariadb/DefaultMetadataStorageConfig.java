package dev.langchain4j.store.embedding.mariadb;

import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * Metadata configuration implementation
 */
public record DefaultMetadataStorageConfig(
        MetadataStorageMode storageMode, List<String> columnDefinitions, List<String> indexes)
        implements MetadataStorageConfig {

    /**
     * Default configuration
     *
     * @return Default configuration
     */
    public static MetadataStorageConfig defaultConfig() {
        return builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON NULL"))
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private MetadataStorageMode storageMode;
        private List<String> columnDefinitions;
        private List<String> indexes;

        @NonNull
        public Builder storageMode(@NonNull MetadataStorageMode storageMode) {
            this.storageMode = storageMode;
            return this;
        }

        @NonNull
        public Builder columnDefinitions(@NonNull List<String> columnDefinitions) {
            this.columnDefinitions = columnDefinitions;
            return this;
        }

        @NonNull
        public Builder indexes(@NonNull List<String> indexes) {
            this.indexes = indexes;
            return this;
        }

        @NonNull
        public DefaultMetadataStorageConfig build() {
            return new DefaultMetadataStorageConfig(this.storageMode, this.columnDefinitions, this.indexes);
        }
    }
}
