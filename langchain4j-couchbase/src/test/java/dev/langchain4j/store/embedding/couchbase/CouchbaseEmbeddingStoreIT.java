package dev.langchain4j.store.embedding.couchbase;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CouchbaseEmbeddingStoreIT extends EmbeddingStoreIT {

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return CouchbaseTestUtils.containerStore();
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return CouchbaseTestUtils.embeddingModel();
    }

    @Override
    protected void clearStore() {
        embeddingStore().removeAll();
    }
}
