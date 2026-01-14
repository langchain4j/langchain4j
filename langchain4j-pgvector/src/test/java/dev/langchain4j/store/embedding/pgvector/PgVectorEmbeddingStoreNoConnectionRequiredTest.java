package dev.langchain4j.store.embedding.pgvector;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import javax.sql.DataSource;

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
