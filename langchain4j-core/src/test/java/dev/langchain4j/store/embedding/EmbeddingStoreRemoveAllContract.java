package dev.langchain4j.store.embedding;

import static org.assertj.core.api.Assertions.assertThatNoException;

import dev.langchain4j.data.segment.TextSegment;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a store honours the contract documented on {@link EmbeddingStore#removeAll(Collection)}:
 * having nothing to remove is a no-op.
 *
 * <p>Implement this interface to check a store. Nothing is removed by these tests: a store that honours the
 * contract returns before it touches its backend, so the instance returned by {@link #embeddingStore()} does
 * not need a working connection. A store that failed to return early would reach an unconnected backend and
 * fail, which is what makes these tests meaningful.
 */
public interface EmbeddingStoreRemoveAllContract {

    /**
     * @return the store to test. A new instance should be returned for every call.
     */
    EmbeddingStore<TextSegment> embeddingStore();

    @Test
    default void should_do_nothing_when_removing_all_by_empty_ids() {
        assertThatNoException().isThrownBy(() -> embeddingStore().removeAll(List.of()));
        assertThatNoException().isThrownBy(() -> embeddingStore().removeAll(Set.of()));
    }

    @Test
    default void should_do_nothing_when_removing_all_by_null_ids() {
        assertThatNoException().isThrownBy(() -> embeddingStore().removeAll((Collection<String>) null));
    }
}
