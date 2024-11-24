package dev.langchain4j.store.embedding.duckdb;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;

class DuckDBEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT{

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    EmbeddingStore<TextSegment> embeddingStore = DuckDBEmbeddingStore.inMemory();

    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        embeddingStore().removeAll();
    }
}
