package dev.langchain4j.store.embedding.weaviate;

import static dev.langchain4j.internal.Utils.randomUUID;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithoutMetadataIT;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.weaviate.WeaviateContainer;

@Testcontainers
class LocalWeaviateLegacyVectorEmbeddingStoreIT extends EmbeddingStoreWithoutMetadataIT {

    @Container
    static WeaviateContainer weaviate = new WeaviateContainer("semitechnologies/weaviate:latest")
            .withEnv("QUERY_DEFAULTS_LIMIT", "25")
            .withEnv("DEFAULT_VECTORIZER_MODULE", "none")
            .withEnv("CLUSTER_HOSTNAME", "node1");

    private final String objectClass = "Test" + randomUUID().replace("-", "");

    private final EmbeddingStore<TextSegment> embeddingStore = WeaviateEmbeddingStore.builder()
            .scheme("http")
            .host(weaviate.getHost())
            .port(weaviate.getFirstMappedPort())
            .grpcPort(weaviate.getMappedPort(50051))
            .objectClass(objectClass)
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

    @Override
    protected void ensureStoreIsReady() {
        // Weaviate 1.39+ creates collections with a named vector, so a collection with a single
        // (unnamed) vector, like the ones created by older Weaviate versions, is created explicitly here
        // The schema endpoint is called directly, since the Weaviate client only creates named vectors.
        String schema = """
                {
                  "class": "%s",
                  "vectorizer": "none",
                  "vectorIndexType": "hnsw",
                  "properties": [{"name": "text", "dataType": ["text"]}]
                }
                """.formatted(objectClass);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + weaviate.getHost() + ":" + weaviate.getFirstMappedPort() + "/v1/schema"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(schema))
                .build();
        try {
            HttpResponse<String> response =
                    HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Failed to create the collection: " + response.body());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void clearStore() {
        embeddingStore.removeAll();
    }
}
