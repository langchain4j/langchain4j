package dev.langchain4j.store.embedding.pgvector;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class PgVectorEmbeddingStoreBuilderTest {

    @Test
    void builder_passes_skipCreateVectorExtension_to_the_store() throws Exception {
        PgVectorEmbeddingStore store = PgVectorEmbeddingStore.builder()
                .host("localhost")
                .port(5432)
                .user("user")
                .password("password")
                .database("database")
                .table("embeddings")
                .dropTableFirst(false)
                .createTable(false)
                .useIndex(false)
                .skipCreateVectorExtension(true)
                .build();

        assertThat(skipCreateVectorExtension(store)).isTrue();
    }

    @Test
    void builder_defaults_skipCreateVectorExtension_to_false() throws Exception {
        PgVectorEmbeddingStore store = PgVectorEmbeddingStore.builder()
                .host("localhost")
                .port(5432)
                .user("user")
                .password("password")
                .database("database")
                .table("embeddings")
                .dropTableFirst(false)
                .createTable(false)
                .useIndex(false)
                .build();

        assertThat(skipCreateVectorExtension(store)).isFalse();
    }

    private static boolean skipCreateVectorExtension(PgVectorEmbeddingStore store) throws Exception {
        Field field = PgVectorEmbeddingStore.class.getDeclaredField("skipCreateVectorExtension");
        field.setAccessible(true);
        return field.getBoolean(store);
    }
}
