package dev.langchain4j.store.embedding.couchbase;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.BeforeEach;

class CouchbaseEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return CouchbaseTestUtils.containerStore();
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return CouchbaseTestUtils.embeddingModel();
    }

    @BeforeEach
    protected void clearStore() {
        embeddingStore().removeAll();
    }

    @Override
    protected boolean supportsRemoveAllByFilter() {
        return false;
    }
}
