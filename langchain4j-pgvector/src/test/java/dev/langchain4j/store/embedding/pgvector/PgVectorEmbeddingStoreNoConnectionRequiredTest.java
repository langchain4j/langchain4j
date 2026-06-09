package dev.langchain4j.store.embedding.pgvector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Field;
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

    @Test
    void datasourceBuilder_without_vectorType_defaults_to_VECTOR() throws Exception {
        DataSource dataSource = Mockito.mock(DataSource.class);

        PgVectorEmbeddingStore store = PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("embeddings")
                .createTable(false)
                .dropTableFirst(false)
                .useIndex(false)
                .build();

        verifyNoInteractions(dataSource);
        assertThat(vectorTypeOf(store)).isEqualTo(PgVectorEmbeddingStore.VectorType.VECTOR);
    }

    @Test
    void hostBuilder_without_vectorType_defaults_to_VECTOR() throws Exception {
        PgVectorEmbeddingStore store = PgVectorEmbeddingStore.builder()
                .host("localhost")
                .port(5432)
                .user("test")
                .password("test")
                .database("test")
                .table("embeddings")
                .createTable(false)
                .dropTableFirst(false)
                .useIndex(false)
                .build();

        assertThat(vectorTypeOf(store)).isEqualTo(PgVectorEmbeddingStore.VectorType.VECTOR);
    }

    private static PgVectorEmbeddingStore.VectorType vectorTypeOf(PgVectorEmbeddingStore store) throws Exception {
        Field field = PgVectorEmbeddingStore.class.getDeclaredField("vectorType");
        field.setAccessible(true);
        return (PgVectorEmbeddingStore.VectorType) field.get(store);
    }
}
