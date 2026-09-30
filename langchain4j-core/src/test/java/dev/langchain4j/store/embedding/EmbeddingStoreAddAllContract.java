package dev.langchain4j.store.embedding;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a store honours the contract documented on {@link EmbeddingStore#addAll(List, List, List)}:
 * lists that disagree in size are rejected, and having no embeddings to add is a no-op.
 *
 * <p>Implement this interface to check a store. Nothing is stored by these tests: validation happens before
 * the store touches its backend, so the instance returned by {@link #embeddingStore()} does not need a working
 * connection. That also makes the no-op tests meaningful, since a store that failed to return early would
 * reach an unconnected backend and fail.
 */
public interface EmbeddingStoreAddAllContract {

    /**
     * @return the store to test. A new instance should be returned for every call.
     */
    EmbeddingStore<TextSegment> embeddingStore();

    private static Embedding embedding() {
        return Embedding.from(new float[] {1.0f, 2.0f, 3.0f});
    }

    @Test
    default void should_throw_when_ids_size_differs_from_embeddings_size() {
        List<String> ids = List.of("id-1", "id-2");
        List<Embedding> embeddings = List.of(embedding(), embedding(), embedding());

        assertThatThrownBy(() -> embeddingStore().addAll(ids, embeddings, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ids size (2) is not equal to embeddings size (3)");
    }

    @Test
    default void should_throw_when_embedded_size_differs_from_embeddings_size() {
        List<String> ids = List.of("id-1", "id-2");
        List<Embedding> embeddings = List.of(embedding(), embedding());
        List<TextSegment> embedded = List.of(TextSegment.from("only-one"));

        assertThatThrownBy(() -> embeddingStore().addAll(ids, embeddings, embedded))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddings size (2) is not equal to embedded size (1)");
    }

    @Test
    default void should_throw_when_ids_is_empty_and_embeddings_is_not() {
        assertThatThrownBy(() -> embeddingStore().addAll(List.of(), List.of(embedding()), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ids size (0) is not equal to embeddings size (1)");
    }

    @Test
    default void should_throw_when_ids_is_null_and_embeddings_is_not() {
        assertThatThrownBy(() -> embeddingStore().addAll(null, List.of(embedding()), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ids size (0) is not equal to embeddings size (1)");
    }

    @Test
    default void should_throw_when_embeddings_is_empty_and_ids_is_not() {
        assertThatThrownBy(() -> embeddingStore().addAll(List.of("id-1"), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ids size (1) is not equal to embeddings size (0)");
    }

    @Test
    default void should_throw_when_embeddings_is_empty_and_embedded_is_not() {
        assertThatThrownBy(() -> embeddingStore().addAll(List.of(), List.of(), List.of(TextSegment.from("orphan"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddings size (0) is not equal to embedded size (1)");
    }

    @Test
    default void should_do_nothing_when_there_is_nothing_to_add() {
        assertThatNoException().isThrownBy(() -> embeddingStore().addAll(List.of(), List.of(), null));
        assertThatNoException().isThrownBy(() -> embeddingStore().addAll(List.of(), List.of(), List.of()));
        assertThatNoException().isThrownBy(() -> embeddingStore().addAll(null, null, null));
    }
}
