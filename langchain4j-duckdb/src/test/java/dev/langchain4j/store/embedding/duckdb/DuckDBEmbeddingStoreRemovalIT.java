package dev.langchain4j.store.embedding.duckdb;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;

public class DuckDBEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    EmbeddingStore<TextSegment> embeddingStore = DuckDBEmbeddingStore.inMemory();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }
}
