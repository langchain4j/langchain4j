package dev.langchain4j.store.embedding.pgvector;

import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.Collections;

@Testcontainers
public class PgVectorEmbeddingStoreWithJSONBMultiIndexesFilteringIT extends PgVectorEmbeddingStoreConfigIT {
    @BeforeAll
    static void beforeAll() {
        PgVectorEmbeddingStoreConfigIT.beforeAll();
        embeddingStore = DataSourcePgVectorEmbeddingStore.withDataSourceBuilder()
                .datasource(dataSource)
                .table(TABLE_NAME)
                .dimension(TABLE_DIMENSION)
                .dropTableFirst(true)
                .metadataType("JSONB")
                .metadataDefinition(Collections.singletonList("metadata_b JSONB NULL"))
                .metadataIndexes(Arrays.asList("(metadata_b->'key')", "(metadata_b->'name')", "(metadata_b->'age')"))
                .metadataIndexType("GIN")
                .build();
    }
}
