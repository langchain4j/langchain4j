package dev.langchain4j.store.embedding.pgvector;

import java.util.Collections;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PgVectorEmbeddingStoreWithJSONBFilteringIT extends PgVectorEmbeddingStoreConfigIT {
    @BeforeAll
    static void beforeAll() {
        MetadataStorageConfig config = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSONB)
                .columnDefinitions(Collections.singletonList("metadata JSONB NULL"))
                .indexes(Collections.singletonList("metadata"))
                .indexType("GIN")
                .build();
        PgVectorEmbeddingStoreConfigIT.configureStore(config);
    }
}
