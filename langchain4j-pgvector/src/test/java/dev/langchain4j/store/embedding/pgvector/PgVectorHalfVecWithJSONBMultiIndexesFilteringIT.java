package dev.langchain4j.store.embedding.pgvector;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Metadata filtering tests for halfvec using {@link MetadataStorageMode#COMBINED_JSONB}
 * with per-key GIN indexes instead of a single index on the whole column.
 */
@Testcontainers
class PgVectorHalfVecWithJSONBMultiIndexesFilteringIT extends PgVectorEmbeddingStoreHalfVecConfigIT {

    @BeforeAll
    static void beforeAll() {
        MetadataStorageConfig config = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSONB)
                .columnDefinitions(Collections.singletonList("metadata_b JSONB NULL"))
                .indexes(Arrays.asList("(metadata_b->'key')", "(metadata_b->'name')", "(metadata_b->'age')"))
                .indexType("GIN")
                .build();
        PgVectorEmbeddingStoreHalfVecConfigIT.configureStore(config);
    }
}