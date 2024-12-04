package dev.langchain4j.store.embedding.mariadb;

import java.util.Collections;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class MariaDbEmbeddingStoreWithJSONFilteringTest extends MariaDbEmbeddingStoreConfigTest {
    @BeforeAll
    static void beforeAll() {
        MetadataStorageConfig config = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON NULL"))
                .build();
        configureStore(config);
    }
}
