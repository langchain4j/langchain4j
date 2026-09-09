package dev.langchain4j.store.embedding.weaviate;

import static dev.langchain4j.internal.Utils.randomUUID;
import static java.util.Collections.singletonList;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithoutMetadataIT;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.v1.schema.model.DataType;
import io.weaviate.client.v1.schema.model.Property;
import io.weaviate.client.v1.schema.model.WeaviateClass;
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
        WeaviateClient client = new WeaviateClient(
                new Config("http", weaviate.getHost() + ":" + weaviate.getFirstMappedPort()));
        client.schema()
                .classCreator()
                .withClass(WeaviateClass.builder()
                        .className(objectClass)
                        .vectorizer("none")
                        .vectorIndexType("hnsw")
                        .properties(singletonList(Property.builder()
                                .name("text")
                                .dataType(singletonList(DataType.TEXT))
                                .build()))
                        .build())
                .run();
    }

    @Override
    protected void clearStore() {
        embeddingStore.removeAll();
    }
}
