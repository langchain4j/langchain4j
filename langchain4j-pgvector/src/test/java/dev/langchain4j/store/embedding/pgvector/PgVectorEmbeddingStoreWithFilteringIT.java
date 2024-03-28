package dev.langchain4j.store.embedding.pgvector;

import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class PgVectorEmbeddingStoreWithFilteringIT extends PgVectorEmbeddingStoreConfigIT {
    @BeforeAll
    static void beforeAll() {
        PgVectorEmbeddingStoreConfigIT.beforeAll();
        embeddingStore = DataSourcePgVectorEmbeddingStore.withDataSourceBuilder()
                .datasource(dataSource)
                .table(TABLE_NAME)
                .dimension(TABLE_DIMENSION)
                .dropTableFirst(true)
                .build();
    }
}
