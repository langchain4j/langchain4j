package dev.langchain4j.store.embedding.inmemory;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;

/**
 * Tests if InMemoryEmbeddingStore works correctly after being serialized and deserialized back.
 * See awaitUntilPersisted()
 */
class InMemoryEmbeddingStoreSerializedTest extends EmbeddingStoreWithFilteringIT {

    InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected void awaitUntilPersisted() {
        String json = embeddingStore.serializeToJson();
        embeddingStore = InMemoryEmbeddingStore.fromJson(json);
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }
}
