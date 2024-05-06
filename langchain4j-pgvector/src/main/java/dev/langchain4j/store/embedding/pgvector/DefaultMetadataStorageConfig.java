package dev.langchain4j.store.embedding.pgvector;

import lombok.*;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.List;

/**
 * Metadata configuration implementation
 */
@Builder
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public class DefaultMetadataStorageConfig implements MetadataStorageConfig {
    @NonNull
    private MetadataStorageMode storageMode;
    @NonNull
    private List<String> columnDefinitions;
    private List<String> indexes;
    private String indexType;

    /**
     * Just for warnings ?
     */
    @SuppressWarnings("unused")
    public DefaultMetadataStorageConfig(){
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
