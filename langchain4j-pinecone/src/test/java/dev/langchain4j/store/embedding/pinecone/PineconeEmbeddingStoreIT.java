package dev.langchain4j.store.embedding.pinecone;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import lombok.SneakyThrows;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static dev.langchain4j.internal.Utils.randomUUID;

@EnabledIfEnvironmentVariable(named = "PINECONE_API_KEY", matches = ".+")
class PineconeEmbeddingStoreIT extends EmbeddingStoreIT {

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    EmbeddingStore<TextSegment> embeddingStore = PineconeEmbeddingStore.builder()
            .apiKey(System.getenv("PINECONE_API_KEY"))
            .cloud("AWS")
            .region("us-east-1")
            .index("test")
            .nameSpace(randomUUID())
            .dimension(embeddingModel.dimension())
            .createIndex(true)
            .build();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    @SneakyThrows
    protected void awaitUntilPersisted() {
        Thread.sleep(6000);
    }
}