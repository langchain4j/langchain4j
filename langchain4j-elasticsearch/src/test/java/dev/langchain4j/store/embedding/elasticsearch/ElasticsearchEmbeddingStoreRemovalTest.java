package dev.langchain4j.store.embedding.elasticsearch;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.elasticsearch.indices.RefreshResponse;
import co.elastic.clients.util.ObjectBuilder;
import java.io.IOException;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ElasticsearchEmbeddingStoreRemovalTest {

    private final ElasticsearchClient client = mock(ElasticsearchClient.class, RETURNS_SELF);
    private final ElasticsearchIndicesClient indices = mock(ElasticsearchIndicesClient.class);
    private final ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
            .client(client)
            .indexName("removal-test")
            .build();

    @Test
    void should_not_delete_when_refresh_request_fails() throws IOException {
        when(client.indices()).thenReturn(indices);
        IOException failure = new IOException("refresh unavailable");
        when(indices.refresh(anyRefreshRequest())).thenThrow(failure);

        assertThatThrownBy(() -> store.removeAll(metadataKey("version").isEqualTo("old")))
                .isInstanceOf(ElasticsearchRequestFailedException.class)
                .hasCause(failure);
        verify(client, never()).deleteByQuery(any(Function.class));
    }

    @Test
    void should_not_delete_when_some_shards_fail_to_refresh() throws IOException {
        when(client.indices()).thenReturn(indices);
        when(indices.refresh(anyRefreshRequest()))
                .thenReturn(RefreshResponse.of(
                        r -> r.shards(s -> s.total(2).successful(1).failed(1))));

        assertThatThrownBy(() -> store.removeAll(metadataKey("version").isEqualTo("old")))
                .isInstanceOf(ElasticsearchRequestFailedException.class)
                .hasMessageContaining("Failed to refresh all shards");
        verify(client, never()).deleteByQuery(any(Function.class));
    }

    private static Function<RefreshRequest.Builder, ObjectBuilder<RefreshRequest>> anyRefreshRequest() {
        return any();
    }
}
