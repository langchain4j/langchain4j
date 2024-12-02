package dev.langchain4j.store.embedding.qdrant;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

import java.util.concurrent.ExecutionException;

import static dev.langchain4j.internal.Utils.randomUUID;
import static io.qdrant.client.grpc.Collections.Distance.Cosine;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class QdrantEmbeddingStoreWithRemovalIT extends EmbeddingStoreWithRemovalIT {

    private static final String COLLECTION_NAME = "langchain4j-" + randomUUID();

    @Container
    private static final QdrantContainer QDRANT_CONTAINER = new QdrantContainer("qdrant/qdrant:latest");

    private static QdrantEmbeddingStore EMBEDDING_STORE;
    private static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void setup() throws InterruptedException, ExecutionException {
        EMBEDDING_STORE = QdrantEmbeddingStore.builder()
                .host(QDRANT_CONTAINER.getHost())
                .port(QDRANT_CONTAINER.getGrpcPort())
                .collectionName(COLLECTION_NAME)
                .build();

        QdrantClient client = new QdrantClient(
                QdrantGrpcClient.newBuilder(QDRANT_CONTAINER.getHost(), QDRANT_CONTAINER.getGrpcPort(), false)
                        .build());

        client
                .createCollectionAsync(
                        COLLECTION_NAME,
                        Collections.VectorParams.newBuilder()
                                .setDistance(Cosine)
                                .setSize(EMBEDDING_MODEL.dimension())
                                .build())
                .get();

        client.close();
    }

    @AfterAll
    static void teardown() {
        EMBEDDING_STORE.close();
    }

    @BeforeEach
    void beforeEach() {
        clearStore();
        ensureStoreIsEmpty();
    }

    protected void clearStore() {
        EMBEDDING_STORE.clearStore();
    }

    protected void ensureStoreIsEmpty() {
        assertThat(getAllEmbeddings()).isEmpty();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return EMBEDDING_STORE;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return EMBEDDING_MODEL;
    }
}
