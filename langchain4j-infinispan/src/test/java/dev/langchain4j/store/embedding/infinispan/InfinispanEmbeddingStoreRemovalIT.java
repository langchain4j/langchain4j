package dev.langchain4j.store.embedding.infinispan;

import static org.infinispan.server.test.core.InfinispanContainer.DEFAULT_PASSWORD;
import static org.infinispan.server.test.core.InfinispanContainer.DEFAULT_USERNAME;
import static org.infinispan.server.test.core.InfinispanContainer.IMAGE_BASENAME;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import java.io.IOException;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.util.Version;
import org.infinispan.server.test.core.InfinispanContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

class InfinispanEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    static InfinispanContainer infinispan = new InfinispanContainer(IMAGE_BASENAME + ":" + Version.getVersion());
    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    EmbeddingStore<TextSegment> embeddingStore;

    @BeforeAll
    static void beforeAll() {
        infinispan.start();
    }

    @AfterAll
    static void afterAll() {
        infinispan.stop();
    }

    @BeforeEach
    void createEmbeddingStore() throws IOException {
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.addServer()
                .host(infinispan.getHost())
                .port(infinispan.getFirstMappedPort())
                .security()
                .authentication()
                .username(DEFAULT_USERNAME)
                .password(DEFAULT_PASSWORD);
        builder.socketTimeout(5000);
        builder.maxRetries(10);
        InfinispanEmbeddingStore embeddingStoreInf = InfinispanEmbeddingStore.builder()
                .cacheName("my-cache")
                .dimension(embeddingModel.dimension())
                .infinispanConfigBuilder(builder)
                .build();
        embeddingStoreInf.clearCache();
        embeddingStore = embeddingStoreInf;
    }

    @AfterEach
    void removeDataStore() {
        if (embeddingStore != null) {
            embeddingStore.removeAll();
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
}
