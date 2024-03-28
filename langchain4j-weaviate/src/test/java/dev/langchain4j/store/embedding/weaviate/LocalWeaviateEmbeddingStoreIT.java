package dev.langchain4j.store.embedding.weaviate;

import dev.langchain4j.data.segment.TextSegment;
import static dev.langchain4j.internal.Utils.randomUUID;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.testcontainers.junit.jupiter.Testcontainers;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import io.weaviate.client.v1.schema.model.Property;
import java.util.Arrays;
import java.util.Collections;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.weaviate.WeaviateContainer;

@Testcontainers
class LocalWeaviateEmbeddingStoreIT extends EmbeddingStoreIT {

    @Container
    static WeaviateContainer weaviate = new WeaviateContainer("semitechnologies/weaviate:latest")
            .withEnv("QUERY_DEFAULTS_LIMIT", "25")
            .withEnv("DEFAULT_VECTORIZER_MODULE", "none")
            .withEnv("AUTHENTICATION_ANONYMOUS_ACCESS_ENABLED", "true")
            .withEnv("CLUSTER_HOSTNAME", "node1");

    private final EmbeddingStore<TextSegment> embeddingStore = WeaviateEmbeddingStore.builder()
            .scheme("http")
            .host(weaviate.getHost())
            .port(weaviate.getMappedPort(8080))
            .useGrpc(true)
            .grpcPort(weaviate.getMappedPort(50051))
            .objectClass("Test" + randomUUID().replace("-", ""))
            .properties(Arrays.asList(new Property[]{
        Property.builder().name("string_empty").dataType(Collections.singletonList("text")).build(),
        Property.builder().name("string_space").dataType(Collections.singletonList("text")).build(),
        Property.builder().name("string_abc").dataType(Collections.singletonList("text")).build(),
        Property.builder().name("integer_min").dataType(Collections.singletonList("int")).build(),
        Property.builder().name("integer_minus_1").dataType(Collections.singletonList("int")).build(),
        Property.builder().name("integer_0").dataType(Collections.singletonList("int")).build(),
        Property.builder().name("integer_1").dataType(Collections.singletonList("int")).build(),
        Property.builder().name("integer_max").dataType(Collections.singletonList("int")).build(),
        Property.builder().name("long_min").dataType(Collections.singletonList("int")).build(),
        Property.builder().name("long_minus_1").dataType(Collections.singletonList("int")).build(),
        Property.builder().name("long_0").dataType(Collections.singletonList("int")).build(),
        Property.builder().name("long_1").dataType(Collections.singletonList("int")).build(),
        Property.builder().name("long_max").dataType(Collections.singletonList("int")).build(),
        Property.builder().name("float_min").dataType(Collections.singletonList("number")).build(),
        Property.builder().name("float_minus_1").dataType(Collections.singletonList("number")).build(),
        Property.builder().name("float_0").dataType(Collections.singletonList("number")).build(),
        Property.builder().name("float_1").dataType(Collections.singletonList("number")).build(),
        Property.builder().name("float_123").dataType(Collections.singletonList("number")).build(),
        Property.builder().name("float_max").dataType(Collections.singletonList("number")).build(),
        Property.builder().name("double_minus_1").dataType(Collections.singletonList("number")).build(),
        Property.builder().name("double_0").dataType(Collections.singletonList("number")).build(),
        Property.builder().name("double_1").dataType(Collections.singletonList("number")).build(),
        Property.builder().name("double_123").dataType(Collections.singletonList("number")).build()
    }))
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
    protected void ensureStoreIsEmpty() {
        // TODO fix
    }
}
