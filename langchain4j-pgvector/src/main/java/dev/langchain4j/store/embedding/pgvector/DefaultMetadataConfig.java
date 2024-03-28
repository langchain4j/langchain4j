package dev.langchain4j.store.embedding.pgvector;

import lombok.*;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Metadata configuration implementation
 */
@Builder
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public class DefaultMetadataConfig implements MetadataConfig {
    @NonNull
    private String type;
    @NonNull
    private List<String> definition;
    private List<String> indexes;
    private String indexType;

    /**
     * Just for warnings ?
     */
    @SuppressWarnings("unused")
    public DefaultMetadataConfig(){
        // Just for javadoc warning ?
    }

    /**
     * Default configuration
     *
     * @return Default configuration
     */
    public static MetadataConfig defaultConfig() {
        return DefaultMetadataConfig.builder()
                .type("JSON")
                .definition(Collections.singletonList("metadata JSON NULL"))
                .build();
    }

    /**
     * indexes as Optional
     * @return indexes as Optional
     */
    public Optional<List<String>>indexes() {
        return Optional.ofNullable(indexes);
    }
}
