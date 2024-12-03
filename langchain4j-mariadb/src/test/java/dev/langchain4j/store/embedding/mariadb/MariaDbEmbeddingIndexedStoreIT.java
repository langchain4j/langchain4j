package dev.langchain4j.store.embedding.mariadb;

import static org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextInt;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MariaDbEmbeddingIndexedStoreIT extends EmbeddingStoreWithFilteringIT {

    @Container
    static MariaDBContainer<?> mariadbContainer =
            new MariaDBContainer<>(MariaDBImage.DEFAULT_IMAGE);

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private EmbeddingStore<TextSegment> embeddingStore;

    @Override
    protected void ensureStoreIsReady() {
        embeddingStore =
                MariaDbEmbeddingStore.builder()
                        .host(mariadbContainer.getHost())
                        .port(mariadbContainer.getFirstMappedPort())
                        .user(mariadbContainer.getUsername())
                        .password(mariadbContainer.getPassword())
                        .database(mariadbContainer.getDatabaseName())
                        .table("test" + nextInt(1, 1000))
                        .dimension(embeddingModel.dimension())
                        .dropTableFirst(true)
                        .build();
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
