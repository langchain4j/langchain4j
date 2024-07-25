package dev.langchain4j.store.embedding.couchbase;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.couchbase.CouchbaseEmbeddingStore;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static dev.langchain4j.internal.Utils.randomUUID;

@Disabled
@EnabledIfEnvironmentVariable(named = "CB_CLUSTER", matches = ".+")
class CouchbaseEmbeddingStoreCloudIT extends EmbeddingStoreIT {

    EmbeddingStore<TextSegment> embeddingStore = new CouchbaseEmbeddingStore.Builder(System.getenv("CB_CLUSTER"))
            .username(System.getenv("CB_USERNAME"))
            .password(System.getenv("CB_PASSWORD"))
            .bucketName(System.getenv("CB_BUCKET"))
            .scopeName(System.getenv("CB_SCOPE"))
            .collectionName(System.getenv("CB_COLLECTION"))
            .searchIndexName(System.getenv("CB_FTS_INDEX"))
            .dimensions(CouchbaseEmbeddingStoreIT.TEST_DIMENSIONS)
            .build();

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
