package dev.langchain4j.store.embedding.elasticsearch;

import static dev.langchain4j.internal.Utils.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.Refresh;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ElasticsearchEmbeddingStoreRefreshIT {

    private static final ElasticsearchClientHelper HELPER = new ElasticsearchClientHelper();

    private String indexName;

    @BeforeAll
    static void startServices() throws IOException {
        HELPER.startServices();
    }

    @AfterAll
    static void stopServices() throws IOException {
        HELPER.stopServices();
    }

    @BeforeEach
    void createIndex() throws IOException {
        indexName = randomUUID();
        HELPER.client
                .indices()
                .create(c -> c.index(indexName)
                        .settings(s -> s.numberOfShards("1").numberOfReplicas("0"))
                        .mappings(m -> m.properties("vector", p -> p.denseVector(v -> v.dims(4)))));
    }

    @AfterEach
    void removeIndex() throws IOException {
        HELPER.removeDataStore(indexName);
    }

    @ParameterizedTest
    @EnumSource(
            value = Refresh.class,
            names = {"True", "WaitFor"})
    void should_make_vector_and_text_writes_searchable_before_returning(Refresh refresh) throws IOException {
        ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
                .client(HELPER.client)
                .indexName(indexName)
                .refresh(refresh)
                .build();

        writeDocuments(store);

        assertThat(HELPER.client.count(c -> c.index(indexName)).count()).isEqualTo(2);
    }

    @Test
    void should_leave_search_visibility_to_index_refreshes_by_default() throws IOException {
        disableAutomaticRefresh();
        ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
                .client(HELPER.client)
                .indexName(indexName)
                .build();

        writeDocuments(store);

        assertThat(HELPER.client.exists(e -> e.index(indexName).id("vector")).value())
                .isTrue();
        assertThat(HELPER.client.exists(e -> e.index(indexName).id("text")).value())
                .isTrue();
        assertThat(HELPER.client.count(c -> c.index(indexName)).count()).isZero();

        HELPER.refreshIndex(indexName);
        assertThat(HELPER.client.count(c -> c.index(indexName)).count()).isEqualTo(2);
    }

    @Test
    void should_make_removal_by_id_searchable_before_returning() throws IOException {
        disableAutomaticRefresh();
        ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
                .client(HELPER.client)
                .indexName(indexName)
                .refresh(Refresh.True)
                .build();
        writeDocuments(store);
        assertThat(HELPER.client.count(c -> c.index(indexName)).count()).isEqualTo(2);

        store.removeAll(List.of("vector", "text"));

        assertThat(HELPER.client.count(c -> c.index(indexName)).count()).isZero();
    }

    private void disableAutomaticRefresh() throws IOException {
        HELPER.client
                .indices()
                .putSettings(p -> p.index(indexName).settings(s -> s.refreshInterval(t -> t.time("-1"))));
    }

    private static void writeDocuments(ElasticsearchEmbeddingStore store) {
        store.addAll(
                List.of("vector"),
                List.of(Embedding.from(new float[] {1, 0, 0, 0})),
                List.of(TextSegment.from("vector chunk")));
        store.add("text", "text chunk");
    }
}
