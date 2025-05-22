package dev.langchain4j.store.embedding.mariadb;

import java.util.Collections;
import org.junit.jupiter.api.BeforeAll;

class MariaDbEmbeddingStoreWithJSONFilteringIT extends MariaDbEmbeddingStoreConfigIT {
    @BeforeAll
    static void beforeAllTests() {
        MetadataStorageConfig config = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON NULL"))
                .build();
        configureStore(config);
    }
}
