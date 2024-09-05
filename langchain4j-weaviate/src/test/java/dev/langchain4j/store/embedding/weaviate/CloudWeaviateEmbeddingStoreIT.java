package dev.langchain4j.store.embedding.weaviate;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateAuthClient;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.v1.auth.exception.AuthException;
import org.junit.jupiter.api.Disabled;

import static dev.langchain4j.internal.Utils.randomUUID;

@Disabled("Run manually before release. Free sandbox expires every 14 days.")
class CloudWeaviateEmbeddingStoreIT extends EmbeddingStoreIT {

    private static final String WEAVIATE_API_KEY = "";
    private static final String WEAVIATE_HOST = "";

    String objectClass = "Test" + randomUUID().replace("-", "");

    EmbeddingStore<TextSegment> embeddingStore = WeaviateEmbeddingStore.builder()
            .apiKey(WEAVIATE_API_KEY)
            .scheme("https")
            .host(WEAVIATE_HOST)
            .objectClass(objectClass)
            .metadataKeys(LocalWeaviateEmbeddingStoreIT.METADATA_KEYS)
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
            WeaviateClient client = WeaviateAuthClient.apiKey(new Config("https", WEAVIATE_HOST), WEAVIATE_API_KEY);
            client.batch().objectsBatchDeleter()
                    .withClassName(objectClass)
                    .run();
        } catch (AuthException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    protected void ensureStoreIsEmpty() {
        // TODO fix
    }
}