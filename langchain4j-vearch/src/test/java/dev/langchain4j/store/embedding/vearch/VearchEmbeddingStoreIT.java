package dev.langchain4j.store.embedding.vearch;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;

public class VearchEmbeddingStoreIT extends EmbeddingStoreIT {

    static GenericContainer<?> vearch = new GenericContainer<>(DockerImageName.parse("vearch/vearch:latest"));

    EmbeddingStore<TextSegment> embeddingStore = VearchEmbeddingStore.builder()
            .baseUrl("http://localhost:8817")
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void beforeAll() {
        vearch.setPortBindings(Arrays.asList("8817:8817", "9001:9001"));
        vearch.start();
    }

    @AfterAll
    static void afterAll() {
        vearch.stop();
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
