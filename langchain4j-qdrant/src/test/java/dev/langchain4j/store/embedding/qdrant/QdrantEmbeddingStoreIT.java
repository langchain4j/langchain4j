package dev.langchain4j.store.embedding.qdrant;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

import java.util.concurrent.ExecutionException;

import static dev.langchain4j.internal.Utils.randomUUID;

@Testcontainers
class QdrantEmbeddingStoreIT extends EmbeddingStoreIT {

  private static String collectionName = "langchain4j-" + randomUUID();
  private static int dimension = 384;
  private static int grpcPort = 6334;
  private static Distance distance = Distance.Cosine;
  private static QdrantEmbeddingStore embeddingStore;

  @Container
  private static final QdrantContainer qdrant = new QdrantContainer("qdrant/qdrant:latest");

  EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

  @BeforeAll
  static void setup() throws InterruptedException, ExecutionException {
    embeddingStore =
        QdrantEmbeddingStore.builder()
            .host(qdrant.getHost())
            .port(qdrant.getMappedPort(grpcPort))
            .collectionName(collectionName)
            .build();

    QdrantClient client =
        new QdrantClient(
            QdrantGrpcClient.newBuilder(qdrant.getHost(), qdrant.getMappedPort(grpcPort), false)
                .build());

    client
        .createCollectionAsync(
            collectionName,
            VectorParams.newBuilder().setDistance(distance).setSize(dimension).build())
        .get();

    client.close();
  }

  @AfterAll
  static void teardown() {
    embeddingStore.close();
  }

  @Override
  protected EmbeddingStore<TextSegment> embeddingStore() {
    return embeddingStore;
  }

  @Override
  protected EmbeddingModel embeddingModel() {
    return embeddingModel;
  }

  @Override
  protected void clearStore() {
    embeddingStore.clearStore();
  }
}
