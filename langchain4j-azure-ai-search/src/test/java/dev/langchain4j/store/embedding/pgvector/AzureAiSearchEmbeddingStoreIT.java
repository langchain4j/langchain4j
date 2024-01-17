package dev.langchain4j.store.embedding.pgvector;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.BeforeEach;

public class AzureAiSearchEmbeddingStoreIT extends EmbeddingStoreIT {

    EmbeddingStore<TextSegment> embeddingStore;

    @BeforeEach
    void beforeEach() {

    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return null;
    }
}
