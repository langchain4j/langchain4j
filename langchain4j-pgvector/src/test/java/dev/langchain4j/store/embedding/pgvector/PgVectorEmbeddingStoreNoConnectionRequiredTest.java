package dev.langchain4j.store.embedding.pgvector;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PgVectorEmbeddingStoreNoConnectionRequiredTest {

    @Test
    void noConnectionRequired() {
        DataSource dataSource = Mockito.mock(DataSource.class);

        PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("embeddings")
                .dropTableFirst(false)
                .createTable(false)
                .useIndex(false)
                .build();

        Mockito.verifyNoInteractions(dataSource);
    }
}
