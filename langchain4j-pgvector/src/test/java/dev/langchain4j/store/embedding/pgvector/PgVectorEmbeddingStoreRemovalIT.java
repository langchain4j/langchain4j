package dev.langchain4j.store.embedding.pgvector;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextInt;

@Testcontainers
class PgVectorEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    final EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder()
        .host(pgVector.getHost())
        .port(pgVector.getFirstMappedPort())
        .user("test")
        .password("test")
        .database("test")
        .table("test" + nextInt(2000, 3000))
        .dimension(384)
        .dropTableFirst(true)
        .build();

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
