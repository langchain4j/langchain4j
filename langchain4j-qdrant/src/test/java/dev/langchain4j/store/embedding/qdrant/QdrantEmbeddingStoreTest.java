package dev.langchain4j.store.embedding.qdrant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.SettableFuture;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that an interrupted call restores the thread's interrupt status before the
 * {@link RuntimeException} leaves the store, so that a caller relying on the flag (for example a
 * timeout wrapper, or any deadline based cancellation) is not silently left with it cleared. Also
 * pins the other side of the same guard: a call that fails for an unrelated reason must leave the
 * flag alone.
 */
class QdrantEmbeddingStoreTest {

    private static final Embedding EMBEDDING = Embedding.from(new float[] {1f, 2f, 3f});

    private final QdrantClient client = mock(QdrantClient.class);

    @AfterEach
    void clearInterruptStatus() {
        Thread.interrupted();
    }

    @Test
    void should_restore_interrupt_status_when_add_all_is_interrupted() {
        when(client.upsertAsync(anyString(), anyList())).thenReturn(SettableFuture.create());

        assertInterruptStatusIsRestored(() -> store().addAll(List.of("1"), List.of(EMBEDDING), null));
    }

    @Test
    void should_restore_interrupt_status_when_remove_all_by_ids_is_interrupted() {
        when(client.deleteAsync(any(Points.DeletePoints.class))).thenReturn(SettableFuture.create());

        assertInterruptStatusIsRestored(() -> store().removeAll(List.of("1")));
    }

    @Test
    void should_restore_interrupt_status_when_remove_all_by_filter_is_interrupted() {
        when(client.deleteAsync(any(Points.DeletePoints.class))).thenReturn(SettableFuture.create());

        assertInterruptStatusIsRestored(() -> store().removeAll(new IsEqualTo("key", "value")));
    }

    @Test
    void should_restore_interrupt_status_when_search_is_interrupted() {
        when(client.queryAsync(any(Points.QueryPoints.class))).thenReturn(SettableFuture.create());

        assertInterruptStatusIsRestored(() -> store().search(new EmbeddingSearchRequest(EMBEDDING, 10, 0.0, null)));
    }

    @Test
    void should_restore_interrupt_status_when_clear_store_is_interrupted() {
        when(client.deleteAsync(any(Points.DeletePoints.class))).thenReturn(SettableFuture.create());

        assertInterruptStatusIsRestored(() -> store().clearStore());
    }

    @Test
    void should_leave_the_interrupt_status_alone_when_the_call_fails_for_another_reason() {
        SettableFuture<Points.UpdateResult> failed = SettableFuture.create();
        failed.setException(new IllegalStateException("boom"));
        when(client.upsertAsync(anyString(), anyList())).thenReturn(failed);

        assertThatThrownBy(() -> store().addAll(List.of("1"), List.of(EMBEDDING), null))
                .isInstanceOf(RuntimeException.class);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    private QdrantEmbeddingStore store() {
        return QdrantEmbeddingStore.builder()
                .client(client)
                .collectionName("test-collection")
                .build();
    }

    /**
     * A blocking {@code Future.get()} that fails with an {@link InterruptedException} clears the
     * interrupt status, so the status observed afterwards can only be {@code true} if the store
     * put it back.
     */
    private static void assertInterruptStatusIsRestored(ThrowingCallable call) {
        Thread.currentThread().interrupt();
        assertThatThrownBy(call).isInstanceOf(RuntimeException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
}
