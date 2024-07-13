package dev.langchain4j.store.embedding.chroma;

import static dev.langchain4j.internal.Utils.randomUUID;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ChromaEmbeddingStoreIT extends EmbeddingStoreIT {

    @Container
    private static final ChromaDBContainer chroma = new ChromaDBContainer("ghcr.io/chroma-core/chroma:0.5.2");

    EmbeddingStore<TextSegment> embeddingStore = ChromaEmbeddingStore
        .builder()
        .baseUrl(chroma.getEndpoint())
        .collectionName(randomUUID())
        .logRequests(true)
        .logResponses(true)
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
