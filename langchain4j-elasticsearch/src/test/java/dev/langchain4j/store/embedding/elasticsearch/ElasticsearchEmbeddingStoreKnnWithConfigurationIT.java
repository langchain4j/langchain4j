package dev.langchain4j.store.embedding.elasticsearch;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static dev.langchain4j.internal.Utils.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchEmbeddingStoreKnnWithConfigurationIT {

    static ElasticsearchClientHelper elasticsearchClientHelper = new ElasticsearchClientHelper();
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
    void createEmbeddingStore() {
        indexName = randomUUID();
    }

    @AfterEach
    void removeDataStore() throws IOException {
        // We remove the indices in case we were running with a local test instance
        // we don't keep dirty things around
        elasticsearchClientHelper.removeDataStore(indexName);
    }

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Test
    void withNumCandidates() throws IOException {
        // Our dataset
        Embedding embedding1 = embeddingModel.embed("hello").content();
        Embedding embedding2 = embeddingModel.embed("bonjour").content();
        Embedding embedding3 = embeddingModel.embed("buen día").content();
        List<Embedding> embeddings = Arrays.asList(embedding1, embedding2, embedding3);

        // Test with a high numCandidates
        {
            EmbeddingStore<TextSegment> embeddingStore = ElasticsearchEmbeddingStore.builder()
                    .configuration(ElasticsearchConfigurationKnn.builder()
                            .numCandidates(10)
                            .build())
                    .restClient(elasticsearchClientHelper.restClient)
                    .indexName(indexName)
                    .build();

            // given
            embeddingStore.addAll(embeddings);

            // refresh the index
            elasticsearchClientHelper.refreshIndex(indexName);

            // then
            assertThat(embeddingStore.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding1)
                    .maxResults(10)
                    .build()).matches()).hasSize(3);
        }

        // Remove the datastore between tests
        elasticsearchClientHelper.removeDataStore(indexName);

        {
            EmbeddingStore<TextSegment> embeddingStore = ElasticsearchEmbeddingStore.builder()
                    .configuration(ElasticsearchConfigurationKnn.builder()
                            .numCandidates(1)
                            .build())
                    .restClient(elasticsearchClientHelper.restClient)
                    .indexName(indexName)
                    .build();

            // given
            embeddingStore.addAll(embeddings);

            // refresh the index
            elasticsearchClientHelper.refreshIndex(indexName);

            // then
            assertThat(embeddingStore.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding1)
                    .maxResults(10)
                    .build()).matches()).hasSize(1);
        }
    }
}
