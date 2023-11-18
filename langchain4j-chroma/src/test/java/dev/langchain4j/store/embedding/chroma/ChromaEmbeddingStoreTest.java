package dev.langchain4j.store.embedding.chroma;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.AbstractEmbeddingStoreIT;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Disabled;

import static dev.langchain4j.internal.Utils.randomUUID;

@Disabled("needs Chroma running locally")
class ChromaEmbeddingStoreIT extends AbstractEmbeddingStoreIT {

    /**
     * First ensure you have Chroma running locally. If not, then:
     * - Execute "docker pull ghcr.io/chroma-core/chroma:0.4.6"
     * - Execute "docker run -d -p 8000:8000 ghcr.io/chroma-core/chroma:0.4.6"
     * - Wait until Chroma is ready to serve (may take a few minutes)
     */

    private final EmbeddingStore<TextSegment> embeddingStore = ChromaEmbeddingStore.builder()
            .baseUrl("http://localhost:8000")
            .collectionName(randomUUID())
            .build();

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }
}