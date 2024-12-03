package dev.langchain4j.store.embedding.mariadb;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

/**
 * Metadata configuration implementation
 */
@Builder
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public class DefaultMetadataStorageConfig implements MetadataStorageConfig {
    @NonNull private MetadataStorageMode storageMode;
    @NonNull private List<String> columnDefinitions;
    private List<String> indexes;

    /**
     * Just for warnings ?
     */
    @SuppressWarnings("unused")
    public DefaultMetadataStorageConfig() {
        // Just for javadoc warning ?
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
}
