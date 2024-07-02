package dev.langchain4j.store.embedding.pinecone;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;

import static dev.langchain4j.internal.Utils.randomUUID;
import static org.awaitility.Awaitility.await;

@EnabledIfEnvironmentVariable(named = "PINECONE_API_KEY", matches = ".+")
class PineconeEmbeddingStoreIT extends EmbeddingStoreIT {

    EmbeddingStore<TextSegment> embeddingStore = PineconeEmbeddingStore.builder()
            .apiKey(System.getenv("PINECONE_API_KEY"))
            .environment("northamerica-northeast1-gcp")
            .projectId("19a129b")
            .index("test")
            .nameSpace(randomUUID())
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
    protected void awaitUntilPersisted(Embedding embedding, int expectedSize) {
        await()
                .timeout(Duration.ofSeconds(15))
                .until(() -> embeddingStore.findRelevant(embedding, expectedSize).size() == expectedSize);
    }
}
