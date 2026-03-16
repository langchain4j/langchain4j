package dev.langchain4j.store.embedding.pgvector;

import java.util.Collections;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Metadata filtering tests for halfvec using {@link MetadataStorageMode#COMBINED_JSONB}.
 * All metadata is stored in a single JSONB column with a GIN index.
 */
@Testcontainers
class PgVectorHalfVecWithJSONBFilteringIT extends PgVectorEmbeddingStoreHalfVecConfigIT {

    @BeforeAll
    static void beforeAll() {
        MetadataStorageConfig config = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSONB)
                .columnDefinitions(Collections.singletonList("metadata JSONB NULL"))
                .indexes(Collections.singletonList("metadata"))
                .indexType("GIN")
                .build();
        PgVectorEmbeddingStoreHalfVecConfigIT.configureStore(config);
    }
}