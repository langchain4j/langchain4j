package dev.langchain4j.example.elasticsearch;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfiguration.VECTOR_FIELD;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.mapping.DenseVectorIndexOptionsType;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchClientHelper;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ElasticsearchConfigurationCustomIT {
    static ElasticsearchClientHelper elasticsearchClientHelper = new ElasticsearchClientHelper();
    private String indexName;

    @BeforeAll
    static void startServices() throws IOException {
        elasticsearchClientHelper.startServices();
        assertThat(elasticsearchClientHelper.client).isNotNull();
    }

    @AfterAll
    static void stopServices() throws IOException {
        // Comment this line if you want to use "reuse" feature from TestContainers
        elasticsearchClientHelper.stopServices();
    }

    @BeforeEach
    void createEmbeddingStore() throws IOException {
        indexName = randomUUID();
        elasticsearchClientHelper.removeDataStore(indexName);
    }

    @AfterEach
    void removeDataStore() throws IOException {
        // We remove the indices in case we were running with a local test instance
        // we don't keep dirty things around
        elasticsearchClientHelper.removeDataStore(indexName);
    }

    @Test
    void createWithCustomConfigurationImplementation() throws IOException {
        BooleanResponse response = elasticsearchClientHelper.client.indices().exists(c -> c.index(indexName));
        if (!response.value()) {
            elasticsearchClientHelper
                    .client
                    .indices()
                    .create(c -> c.index(indexName)
                            .mappings(m -> m.properties("text", p -> p.text(t -> t))
                                    .properties(
                                            VECTOR_FIELD,
                                            p -> p.denseVector(dv -> dv.indexOptions(
                                                    io -> io.type(DenseVectorIndexOptionsType.Hnsw))))));
        }

        final ElasticsearchConfigurationCustom configurationCustom = new ElasticsearchConfigurationCustom();
        final EmbeddingStore<TextSegment> embeddingStore = ElasticsearchEmbeddingStore.builder()
                .configuration(configurationCustom)
                .client(elasticsearchClientHelper.client)
                .indexName(indexName)
                .build();
        embeddingStore.add(new Embedding(new float[] {0.1f, 0.2f, 0.3f}));
        embeddingStore.add(new Embedding(new float[] {0.2f, 0.3f, 0.4f}));
        elasticsearchClientHelper.client.indices().refresh(r -> r.index(indexName));
        final EmbeddingSearchRequest searchRequest = new EmbeddingSearchRequest(EmbeddingSearchRequest.builder()
                // We normally should find the 2nd vector but we forced in the implementation to return a fake one
                .queryEmbedding(new Embedding(new float[] {0.2f, 0.3f, 0.4f})));
        final EmbeddingSearchResult<TextSegment> result = embeddingStore.search(searchRequest);
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).embedding().vector()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(configurationCustom.customSearchCalled).isTrue();
    }
}
