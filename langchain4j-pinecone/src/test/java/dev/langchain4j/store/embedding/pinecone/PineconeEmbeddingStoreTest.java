package dev.langchain4j.store.embedding.pinecone;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;
import org.junit.jupiter.api.Test;

class PineconeEmbeddingStoreTest {

    private static Embedding embedding() {
        return Embedding.from(new float[] {1.0f, 2.0f, 3.0f});
    }

    @Test
    void addAll_should_throw_when_ids_size_differs_from_embeddings_size() {
        PineconeEmbeddingStore store =
                mock(PineconeEmbeddingStore.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

        List<String> ids = List.of("id-1", "id-2");
        List<Embedding> embeddings = List.of(embedding(), embedding(), embedding());

        assertThatThrownBy(() -> store.addAll(ids, embeddings, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ids size (2) is not equal to embeddings size (3)");
    }

    @Test
    void addAll_should_throw_when_textSegments_size_differs_from_embeddings_size() {
        PineconeEmbeddingStore store =
                mock(PineconeEmbeddingStore.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

        List<String> ids = List.of("id-1", "id-2");
        List<Embedding> embeddings = List.of(embedding(), embedding());
        List<TextSegment> textSegments = List.of(TextSegment.from("only-one"));

        assertThatThrownBy(() -> store.addAll(ids, embeddings, textSegments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddings size (2) is not equal to embedded size (1)");
    }

    @Test
    void addAll_should_throw_when_ids_is_empty_and_embeddings_is_not() {
        PineconeEmbeddingStore store =
                mock(PineconeEmbeddingStore.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

        assertThatThrownBy(() -> store.addAll(List.of(), List.of(embedding()), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ids size (0) is not equal to embeddings size (1)");
    }

    @Test
    void addAll_should_throw_when_embeddings_is_empty_and_ids_is_not() {
        PineconeEmbeddingStore store =
                mock(PineconeEmbeddingStore.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

        assertThatThrownBy(() -> store.addAll(List.of("id-1"), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ids size (1) is not equal to embeddings size (0)");
    }

    @Test
    void addAll_should_throw_when_embeddings_is_empty_and_textSegments_is_not() {
        PineconeEmbeddingStore store =
                mock(PineconeEmbeddingStore.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

        assertThatThrownBy(() -> store.addAll(List.of(), List.of(), List.of(TextSegment.from("orphan"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddings size (0) is not equal to embedded size (1)");
    }

    @Test
    void addAll_should_not_throw_when_ids_and_embeddings_are_both_empty() {
        PineconeEmbeddingStore store =
                mock(PineconeEmbeddingStore.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

        assertThatNoException().isThrownBy(() -> store.addAll(List.of(), List.of(), null));
        assertThatNoException().isThrownBy(() -> store.addAll(List.of(), List.of(), List.of()));
    }
}
