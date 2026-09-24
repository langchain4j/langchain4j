package dev.langchain4j.store.embedding.qdrant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListenableFuture;
import dev.langchain4j.data.embedding.Embedding;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.QueryPoints;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.UpdateResult;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The store wraps {@link InterruptedException} from the blocking Qdrant calls in a
 * {@link RuntimeException}. {@code Future.get()} clears the interrupt status when it throws, so the store has to
 * restore it before rethrowing.
 */
class QdrantEmbeddingStoreInterruptTest {

    private final QdrantClient client = mock(QdrantClient.class);
    private final QdrantEmbeddingStore store = new QdrantEmbeddingStore(client, "collection", "text");

    @AfterEach
    void clearInterruptStatus() {
        Thread.interrupted();
    }

    @Test
    void addAll_restores_interrupt_status() throws Exception {

        ListenableFuture<UpdateResult> interrupted = mock(ListenableFuture.class);
        when(interrupted.get()).thenThrow(new InterruptedException("interrupted"));
        when(client.upsertAsync(anyString(), anyList())).thenReturn(interrupted);

        assertThatThrownBy(() -> store.addAll(List.of(Embedding.from(new float[] {1, 2, 3}))))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(InterruptedException.class);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void search_restores_interrupt_status() throws Exception {

        ListenableFuture<List<ScoredPoint>> interrupted = mock(ListenableFuture.class);
        when(interrupted.get()).thenThrow(new InterruptedException("interrupted"));
        when(client.queryAsync(any(QueryPoints.class))).thenReturn(interrupted);

        assertThatThrownBy(() -> store.search(dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                        .queryEmbedding(Embedding.from(new float[] {1, 2, 3}))
                        .build()))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(InterruptedException.class);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
}
