package dev.langchain4j.store.embedding.couchbase;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import lombok.SneakyThrows;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CouchbaseEmbeddingStoreIT extends EmbeddingStoreIT {

    @Override
    protected void clearStore() {
        Utils.containerStore.get().removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return Utils.containerStore.get();
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return Utils.embeddingModel.get();
    }

    @Override
    protected void ensureStoreIsEmpty() {
        embeddingStore().removeAll();
    }

    @Override
    @SneakyThrows
    protected void awaitUntilPersisted() {
        Thread.sleep(1000);
    }
}
