package dev.langchain4j.store.embedding.couchbase;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import lombok.SneakyThrows;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

@Testcontainers
class CouchbaseEmbeddingStoreIT extends EmbeddingStoreIT {
    public static final Integer TEST_DIMENSIONS = 384;
    private static BucketDefinition testBucketDefinition = new BucketDefinition("test")
            .withPrimaryIndex(true)
            .withQuota(100);

    @Container
    private static final CouchbaseContainer couchbaseContainer =
            new CouchbaseContainer(DockerImageName.parse("couchbase:enterprise").asCompatibleSubstituteFor("couchbase/server"))
//                    .withExposedPorts(8091, 11207, 11210, 11211, 18091, 18092, 18093)
//                    .withExposedPorts(8092, 8093, 8094, 8095)
                    .withCredentials("Administrator", "password")
                    .withBucket(testBucketDefinition)
                    .withStartupTimeout(Duration.ofMinutes(1));

    EmbeddingStore<TextSegment> embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected void clearStore() {
        if (embeddingStore != null) {
            embeddingStore.removeAll();
        }
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        if (embeddingStore == null) {
            couchbaseContainer.start();

            Cluster cluster = Cluster.connect(
                    couchbaseContainer.getConnectionString(),
                    couchbaseContainer.getUsername(),
                    couchbaseContainer.getPassword()
            );

            Bucket bucket = cluster.bucket(testBucketDefinition.getName());
            bucket.waitUntilReady(Duration.ofSeconds(30));

            embeddingStore = new CouchbaseEmbeddingStore(
                    couchbaseContainer.getConnectionString(),
                    couchbaseContainer.getUsername(),
                    couchbaseContainer.getPassword(),
                    testBucketDefinition.getName(),
                    "_default",
                    "_default",
                    "test",
                    TEST_DIMENSIONS
            );
        }
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
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
