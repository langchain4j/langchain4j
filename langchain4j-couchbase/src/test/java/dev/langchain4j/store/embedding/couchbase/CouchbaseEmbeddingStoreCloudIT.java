package dev.langchain4j.store.embedding.couchbase;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.couchbase.CouchbaseEmbeddingStore;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static dev.langchain4j.internal.Utils.randomUUID;

@Disabled
@EnabledIfEnvironmentVariable(named = "CB_CLUSTER", matches = ".+")
class CouchbaseEmbeddingStoreCloudIT extends EmbeddingStoreWithFilteringIT {

    EmbeddingStore<TextSegment> embeddingStore = new CouchbaseEmbeddingStore(
            System.getenv("CB_CLUSTER"),
            System.getenv("CB_USERNAME"),
            System.getenv("CB_PASSWORD"),
            System.getenv("CB_BUCKET"),
            System.getenv("CB_SCOPE"),
            System.getenv("CB_COLLECTION"),
            System.getenv("CB_FTS_INDEX"),
            CouchbaseEmbeddingStoreIT.TEST_DIMENSIONS
    );

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void ensureStoreIsEmpty() {
        embeddingStore.removeAll();
    }

    @Override
    @SneakyThrows
    protected void awaitUntilPersisted() {
        Thread.sleep(1000);
    }
}
