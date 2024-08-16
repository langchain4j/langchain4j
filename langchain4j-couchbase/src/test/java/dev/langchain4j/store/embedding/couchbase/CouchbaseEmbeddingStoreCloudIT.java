package dev.langchain4j.store.embedding.couchbase;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import lombok.SneakyThrows;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "COUCHBASE_CLUSTER_URL", matches = ".+")
class CouchbaseEmbeddingStoreCloudIT extends EmbeddingStoreIT {

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return CouchbaseTestUtils.cloudStore.get();
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return CouchbaseTestUtils.embeddingModel.get();
    }

    @Override
    protected void ensureStoreIsEmpty() {
        CouchbaseTestUtils.cloudStore.get().removeAll();
    }

    @Override
    @SneakyThrows
    protected void awaitUntilPersisted() {
        Thread.sleep(1000);
    }
}
