package dev.langchain4j.store.embedding.inmemory;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import org.awaitility.core.ThrowingRunnable;

/**
 * Tests if {@link InMemoryEmbeddingStore} works correctly after being serialized and deserialized back.
 * See {@link #awaitUntilAsserted}, serialization and deserialization happen there.
 */
class InMemoryEmbeddingStoreSerializedTest extends EmbeddingStoreWithFilteringIT {

    InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected void awaitUntilAsserted(ThrowingRunnable assertion) {
        String json = embeddingStore.serializeToJson();
        embeddingStore = InMemoryEmbeddingStore.fromJson(json);
        super.awaitUntilAsserted(assertion);
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
