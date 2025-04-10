package dev.langchain4j.store.embedding.pgvector;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PgVectorEmbeddingStoreWithJSONBMultiIndexesFilteringIT extends PgVectorEmbeddingStoreConfigIT {
    @BeforeAll
    static void beforeAll() {
        MetadataStorageConfig config = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSONB)
                .columnDefinitions(Collections.singletonList("metadata_b JSONB NULL"))
                .indexes(Arrays.asList("(metadata_b->'key')", "(metadata_b->'name')", "(metadata_b->'age')"))
                .indexType("GIN")
                .build();
        PgVectorEmbeddingStoreConfigIT.configureStore(config);
    }
}
