package dev.langchain4j.store.embedding.pgvector;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.verifyNoInteractions;

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

    /**
     * Verifies that building a halfvec store with {@code createTable=false}, {@code useIndex=false},
     * and {@code dropTableFirst=false} requires no database connection at all.
     */
    @Test
    void noConnectionRequiredWithHalfvec() {
        DataSource dataSource = Mockito.mock(DataSource.class);

        PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("embeddings")
                .dropTableFirst(false)
                .createTable(false)
                .useIndex(false)
                .vectorType(PgVectorEmbeddingStore.VectorType.HALFVEC)
                .build();

        verifyNoInteractions(dataSource);
    }
}
