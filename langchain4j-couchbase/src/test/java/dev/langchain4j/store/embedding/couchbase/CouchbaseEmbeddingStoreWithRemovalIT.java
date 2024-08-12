package dev.langchain4j.store.embedding.couchbase;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class CouchbaseEmbeddingStoreWithRemovalIT extends EmbeddingStoreWithRemovalIT {
    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return Utils.containerStore.get();
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return Utils.embeddingModel.get();
    }

    @Test
    @Disabled("should be enabled once implemented")
    void should_remove_all_by_filter() {
    }

    @Test
    @Disabled("should be enabled once implemented")
    void should_fail_to_remove_all_by_filter_null() {
    }

    @Override
    @SneakyThrows
    protected void awaitUntilPersisted() {
        Thread.sleep(1000);
    }

    @BeforeEach
    protected void ensureStoreIsEmpty() {
        embeddingStore().removeAll();
    }
}
