package dev.langchain4j.store.embedding.vespa;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;

@EnabledIfEnvironmentVariables({
    @EnabledIfEnvironmentVariable(named = "VESPA_URL", matches = ".+"),
    @EnabledIfEnvironmentVariable(named = "VESPA_KEY_PATH", matches = ".+"),
    @EnabledIfEnvironmentVariable(named = "VESPA_CERT_PATH", matches = ".+")
})
public class VespaEmbeddingStoreCloudIT extends EmbeddingStoreIT {

    EmbeddingStore<TextSegment> embeddingStore = VespaEmbeddingStore.builder()
            .url(System.getenv("VESPA_URL"))
            .keyPath(System.getenv("VESPA_KEY_PATH"))
            .certPath(System.getenv("VESPA_CERT_PATH"))
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
