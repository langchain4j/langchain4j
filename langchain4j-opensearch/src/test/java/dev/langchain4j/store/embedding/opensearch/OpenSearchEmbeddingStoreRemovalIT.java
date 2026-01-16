package dev.langchain4j.store.embedding.opensearch;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import java.io.IOException;
import java.net.URISyntaxException;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

class OpenSearchEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    /**
     * To run the tests locally, you don't need to have OpenSearch up-and-running. This implementation
     * uses TestContainers (https://testcontainers.com) and the built-in support for OpenSearch. Thus,
     * if you just execute the tests then a container will be spun up automatically for you.
     */
    @Container
    static OpensearchContainer<?> opensearch =
            new OpensearchContainer<>(DockerImageName.parse("opensearchproject/opensearch:2.10.0"));

    private EmbeddingStore<TextSegment> embeddingStore;
    private OpenSearchClient client;
    private String indexName;

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void startOpenSearch() {
        opensearch.start();
    }

    @BeforeEach
    void createEmbeddingStore() throws URISyntaxException {
        indexName = randomUUID();

        // Create OpenSearchClient externally
        HttpHost host = HttpHost.create(opensearch.getHttpHostAddress());
        client = new OpenSearchClient(ApacheHttpClient5TransportBuilder.builder(host)
                .setMapper(new JacksonJsonpMapper())
                .build());

        // Pass the client to the store
        embeddingStore = OpenSearchEmbeddingStore.builder()
                .openSearchClient(client)
                .indexName(indexName)
                .build();
    }

    @AfterEach
    void removeDataStore() {
        // Clean up the index after each test
        try {
            embeddingStore.removeAll();
        } catch (Exception e) {
            // Ignore if index doesn't exist
        }
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
    @Override // OpenSearchEmbeddingStore behaves differently on removeAll() - the index is removed
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
            assertThat(client.indices().exists(er -> er.index(indexName)).value())
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
        assertThat(client.indices().exists(er -> er.index(indexName)).value()).isFalse();
    }
}
