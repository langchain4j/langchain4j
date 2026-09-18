package dev.langchain4j.store.embedding.qdrant;

import static dev.langchain4j.internal.Utils.randomUUID;
import static io.qdrant.client.grpc.Collections.Distance.Cosine;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.VectorParams;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

@Testcontainers(disabledWithoutDocker = true)
class QdrantEmbeddingStoreCompatibilityIT {

    private static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @ParameterizedTest(name = "Qdrant {0}")
    @ValueSource(strings = {"1.15.5", "1.16.3", "1.17.1", "1.18.3", "1.19.0"})
    void should_add_and_search_embeddings_across_supported_server_versions(String serverVersion)
            throws InterruptedException, ExecutionException {
        try (QdrantContainer container = new QdrantContainer("qdrant/qdrant:" + serverVersion)) {
            container.start();

            String collectionName = "langchain4j-compatibility-" + randomUUID();
            createCollection(container, collectionName);

            QdrantEmbeddingStore embeddingStore = QdrantEmbeddingStore.builder()
                    .host(container.getHost())
                    .port(container.getGrpcPort())
                    .collectionName(collectionName)
                    .build();

            try {
                TextSegment segment = TextSegment.from("Qdrant " + serverVersion);
                Embedding embedding = EMBEDDING_MODEL.embed(segment).content();
                String embeddingId = embeddingStore.add(embedding, segment);

                List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embedding)
                                .maxResults(1)
                                .build())
                        .matches();

                assertThat(matches).singleElement().satisfies(match -> {
                    assertThat(match.embeddingId()).isEqualTo(embeddingId);
                    assertThat(match.embedded()).isEqualTo(segment);
                });
            } finally {
                embeddingStore.close();
            }
        }
    }

    private static void createCollection(QdrantContainer container, String collectionName)
            throws InterruptedException, ExecutionException {
        QdrantClient client =
                new QdrantClient(QdrantGrpcClient.newBuilder(container.getHost(), container.getGrpcPort(), false)
                        .build());
        try {
            client.createCollectionAsync(
                            collectionName,
                            VectorParams.newBuilder()
                                    .setDistance(Cosine)
                                    .setSize(EMBEDDING_MODEL.dimension())
                                    .build())
                    .get();
        } finally {
            client.close();
        }
    }
}
