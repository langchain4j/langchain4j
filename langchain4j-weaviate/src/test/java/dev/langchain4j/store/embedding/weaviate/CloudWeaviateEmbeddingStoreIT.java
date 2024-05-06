package dev.langchain4j.store.embedding.weaviate;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateAuthClient;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.v1.auth.exception.AuthException;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Arrays;

import static dev.langchain4j.internal.Utils.randomUUID;

@EnabledIfEnvironmentVariable(named = "WEAVIATE_API_KEY", matches = ".+")
class CloudWeaviateEmbeddingStoreIT extends EmbeddingStoreIT {

    String objectClass = "Test" + randomUUID().replace("-", "");

    EmbeddingStore<TextSegment> embeddingStore = WeaviateEmbeddingStore.builder()
            .apiKey(System.getenv("WEAVIATE_API_KEY"))
            .scheme("https")
            .host(System.getenv("WEAVIATE_HOST"))
            .objectClass(objectClass)
            .metadataKeys(Arrays.asList(
                    "string_empty",
                    "string_space",
                    "string_abc",
                    "integer_min",
                    "integer_minus_1",
                    "integer_0",
                    "integer_1",
                    "integer_max",
                    "long_min",
                    "long_minus_1",
                    "long_0",
                    "long_1",
                    "long_max",
                    "float_min",
                    "float_minus_1",
                    "float_0",
                    "float_1",
                    "float_123",
                    "float_max",
                    "double_minus_1",
                    "double_0",
                    "double_1",
                    "double_123"
            ))
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
    protected void clearStore() {
        try {
            WeaviateClient client = WeaviateAuthClient.apiKey(new Config("https", System.getenv("WEAVIATE_HOST")), System.getenv("WEAVIATE_API_KEY"));
            client.batch().objectsBatchDeleter()
                    .withClassName(objectClass)
                    .run();
        } catch (AuthException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    protected void ensureStoreIsEmpty() {

    }
}