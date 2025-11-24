package dev.langchain4j.store.embedding.infinispan;

import static org.infinispan.testcontainers.InfinispanContainer.DEFAULT_PASSWORD;
import static org.infinispan.testcontainers.InfinispanContainer.DEFAULT_USERNAME;
import static org.infinispan.testcontainers.InfinispanContainer.IMAGE_BASENAME;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.util.Version;
import org.infinispan.testcontainers.InfinispanContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

class InfinispanEmbeddingStoreWithFilterIT extends EmbeddingStoreWithFilteringIT {

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

    @Override
    protected void clearStore() {
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.addServer()
                .uri(infinispan.getConnectionURI())
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
                .distance(100)
                .build();
        embeddingStoreInf.clearCache();
        embeddingStore = embeddingStoreInf;
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
