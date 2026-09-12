package dev.langchain4j.store.embedding.elasticsearch;

import static dev.langchain4j.data.document.Metadata.metadata;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ElasticsearchEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    static final ElasticsearchClientHelper elasticsearchClientHelper = new ElasticsearchClientHelper();

    EmbeddingStore<TextSegment> embeddingStore = ElasticsearchEmbeddingStore.builder()
            .client(elasticsearchClientHelper.client)
            .indexName(randomUUID())
            .build();

    final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    String indexName;

    @BeforeAll
    static void startServices() throws IOException {
        elasticsearchClientHelper.startServices();
        assertThat(elasticsearchClientHelper.client).isNotNull();
    }

    @AfterAll
    static void stopServices() throws IOException {
        elasticsearchClientHelper.stopServices();
    }

    @BeforeEach
    void createEmbeddingStore() throws IOException {
        indexName = randomUUID();
        elasticsearchClientHelper.removeDataStore(indexName);
        embeddingStore = ElasticsearchEmbeddingStore.builder()
                .client(elasticsearchClientHelper.client)
                .indexName(indexName)
                .build();
    }

    @AfterEach
    void removeDataStore() throws IOException {
        // We remove the indices in case we were running with a local test instance
        // we don't keep dirty things around
        elasticsearchClientHelper.removeDataStore(indexName);
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Test
    void should_remove_recently_added_embeddings_by_filter() throws IOException {
        disableAutomaticRefresh();
        Embedding embedding = embeddingModel.embed("printer setup").content();
        embeddingStore.add(embedding, TextSegment.from("old instructions", metadata("version", "old")));
        String retainedId =
                embeddingStore.add(embedding, TextSegment.from("current instructions", metadata("version", "current")));

        embeddingStore.removeAll(metadataKey("version").isEqualTo("old"));

        elasticsearchClientHelper.refreshIndex(indexName);
        assertThat(getAllEmbeddings()).extracting(match -> match.embeddingId()).containsExactly(retainedId);
    }

    @Test
    void should_remove_visible_and_recently_added_embeddings_by_filter() throws IOException {
        disableAutomaticRefresh();
        Embedding embedding = embeddingModel.embed("printer setup").content();
        embeddingStore.add(embedding, TextSegment.from("visible old instructions", metadata("version", "old")));
        elasticsearchClientHelper.refreshIndex(indexName);
        embeddingStore.add(embedding, TextSegment.from("recent old instructions", metadata("version", "old")));
        String retainedId =
                embeddingStore.add(embedding, TextSegment.from("current instructions", metadata("version", "current")));

        embeddingStore.removeAll(metadataKey("version").isEqualTo("old"));

        elasticsearchClientHelper.refreshIndex(indexName);
        assertThat(getAllEmbeddings()).extracting(match -> match.embeddingId()).containsExactly(retainedId);
    }

    @Test
    void should_remove_recently_updated_embeddings_by_filter() throws IOException {
        disableAutomaticRefresh();
        Embedding embedding = embeddingModel.embed("printer setup").content();
        String updatedId = embeddingStore.add(
                embedding, TextSegment.from("previous instructions", metadata("version", "current")));
        String retainedId =
                embeddingStore.add(embedding, TextSegment.from("current instructions", metadata("version", "current")));
        elasticsearchClientHelper.refreshIndex(indexName);
        embeddingStore.addAll(
                List.of(updatedId),
                List.of(embedding),
                List.of(TextSegment.from("obsolete instructions", metadata("version", "old"))));

        embeddingStore.removeAll(metadataKey("version").isEqualTo("old"));

        elasticsearchClientHelper.refreshIndex(indexName);
        assertThat(getAllEmbeddings()).extracting(match -> match.embeddingId()).containsExactly(retainedId);
    }

    private void disableAutomaticRefresh() throws IOException {
        // Keep writes unrefreshed until removal; a refresh afterwards distinguishes missed deletes from search lag.
        elasticsearchClientHelper
                .client
                .indices()
                .create(c -> c.index(indexName).settings(s -> s.refreshInterval(t -> t.time("-1"))));
    }

    @Test
    @Override // ElasticsearchEmbeddingStore behaves differently on removeAll() - the index is removed
    protected void should_remove_all() {

        // given
        Embedding embedding1 = embeddingModel().embed("test1").content();
        embeddingStore().add(embedding1);

        Embedding embedding2 = embeddingModel().embed("test2").content();
        embeddingStore().add(embedding2);

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(2));

        // when
        embeddingStore().removeAll();

        // then
        try {
            assertThat(elasticsearchClientHelper
                            .client
                            .indices()
                            .exists(er -> er.index(indexName))
                            .value())
                    .isFalse();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void should_not_fail_to_remove_non_existing_datastore() throws IOException {

        // when
        embeddingStore.removeAll();

        // then
        assertThat(elasticsearchClientHelper
                        .client
                        .indices()
                        .exists(er -> er.index(indexName))
                        .value())
                .isFalse();
    }
}
