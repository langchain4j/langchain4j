package dev.langchain4j.store.embedding.pgvector;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PgVectorEmbeddingStoreValidationTest {

    private static final Embedding EMBEDDING = Embedding.from(new float[] {1, 2, 3});

    @Test
    void should_reject_empty_ids_with_non_empty_embeddings() {
        DataSource dataSource = Mockito.mock(DataSource.class);

        assertThatThrownBy(() -> store(dataSource).addAll(emptyList(), singletonList(EMBEDDING), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ids size (0) is not equal to embeddings size (1)");

        Mockito.verifyNoInteractions(dataSource);
    }

    @Test
    void should_reject_empty_embeddings_with_non_empty_embedded() {
        DataSource dataSource = Mockito.mock(DataSource.class);

        assertThatThrownBy(() ->
                        store(dataSource).addAll(emptyList(), emptyList(), singletonList(TextSegment.from("text"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("embeddings size (0) is not equal to embedded size (1)");

        Mockito.verifyNoInteractions(dataSource);
    }

    @Test
    void should_reject_more_ids_than_embeddings() {
        DataSource dataSource = Mockito.mock(DataSource.class);

        assertThatThrownBy(() -> store(dataSource).addAll(List.of("id-1", "id-2"), singletonList(EMBEDDING), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ids size (2) is not equal to embeddings size (1)");

        Mockito.verifyNoInteractions(dataSource);
    }

    @Test
    void should_do_nothing_when_everything_is_empty() {
        DataSource dataSource = Mockito.mock(DataSource.class);

        assertThatCode(() -> store(dataSource).addAll(emptyList(), emptyList(), null))
                .doesNotThrowAnyException();

        Mockito.verifyNoInteractions(dataSource);
    }

    private static PgVectorEmbeddingStore store(DataSource dataSource) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("embeddings")
                .dropTableFirst(false)
                .createTable(false)
                .useIndex(false)
                .build();
    }
}
