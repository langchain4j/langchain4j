package dev.langchain4j.store.embedding.elasticsearch;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static dev.langchain4j.internal.Utils.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    static ElasticsearchClientHelper elasticsearchClientHelper = new ElasticsearchClientHelper();

    EmbeddingStore<TextSegment> embeddingStore = ElasticsearchEmbeddingStore.builder()
            .restClient(elasticsearchClientHelper.restClient)
            .indexName(randomUUID())
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    String indexName;

    @BeforeAll
    static void startServices() throws IOException {
        elasticsearchClientHelper.startServices();
        assertThat(elasticsearchClientHelper.restClient).isNotNull();
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
                .restClient(elasticsearchClientHelper.restClient)
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
    void should_remove_all() throws IOException {

        // given
        Embedding embedding1 = embeddingModel().embed("test1").content();
        embeddingStore().add(embedding1);

        Embedding embedding2 = embeddingModel().embed("test2").content();
        embeddingStore().add(embedding2);

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(2));

        // when
        embeddingStore().removeAll();

        // then
        assertThat(elasticsearchClientHelper.client.indices().exists(er -> er.index(indexName)).value()).isFalse();
    }

    @Test
    void should_not_fail_to_remove_non_existing_datastore() throws IOException {

        // when
        embeddingStore.removeAll();

        // then
        assertThat(elasticsearchClientHelper.client.indices().exists(er -> er.index(indexName)).value()).isFalse();
    }
}
