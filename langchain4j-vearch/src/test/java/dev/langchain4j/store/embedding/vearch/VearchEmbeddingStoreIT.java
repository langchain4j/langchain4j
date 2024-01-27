package dev.langchain4j.store.embedding.vearch;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class VearchEmbeddingStoreIT extends EmbeddingStoreIT {
    EmbeddingStore<TextSegment> embeddingStore;
    @Container
    private static final GenericContainer<?> vearch = new GenericContainer<>("vearch/vearch:latest")
            .withExposedPorts(9001);

    @BeforeEach
    public void setUp() {
        embeddingStore = VearchEmbeddingStore.builder()
                .routerUrl(String.format("http://%s:%d", vearch.getHost(), vearch.getMappedPort(9001)))
                .database("ts_db")
                .space("ts_space")
                .vectorField("ts_vector")
                .textField("ts_keyword")
                .build();

    }

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }
}
