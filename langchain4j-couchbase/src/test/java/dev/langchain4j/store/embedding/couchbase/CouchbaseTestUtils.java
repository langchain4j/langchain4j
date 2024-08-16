package dev.langchain4j.store.embedding.couchbase;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.com.google.common.base.Suppliers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.function.Supplier;

@Testcontainers
final class CouchbaseTestUtils {
    private CouchbaseTestUtils() {

    }
    static final Integer TEST_DIMENSIONS = 384;

    final static Supplier<CouchbaseEmbeddingStore> cloudStore = Suppliers.memoize(() -> new CouchbaseEmbeddingStore.Builder(System.getenv("COUCHBASE_CLUSTER_URL"))
                .username(System.getenv("COUCHBASE_USERNAME"))
                .password(System.getenv("COUCHBASE_PASSWORD"))
                .bucketName(System.getenv("COUCHBASE_BUCKET"))
                .scopeName(System.getenv("COUCHBASE_SCOPE"))
                .collectionName(System.getenv("COUCHBASE_COLLECTION"))
                .searchIndexName(System.getenv("COUCHBASE_FTS_INDEX"))
                .dimensions(TEST_DIMENSIONS)
                .build());


    static BucketDefinition testBucketDefinition = new BucketDefinition("test")
            .withPrimaryIndex(true)
            .withQuota(100);

    @Container
    static final CouchbaseContainer couchbaseContainer =
            new CouchbaseContainer(DockerImageName.parse("couchbase:enterprise").asCompatibleSubstituteFor("couchbase/server"))
                    .withCredentials("Administrator", "password")
                    .withBucket(testBucketDefinition)
                    .withStartupTimeout(Duration.ofMinutes(1));

    final static Supplier<CouchbaseEmbeddingStore> containerStore = Suppliers.memoize(() -> {
        couchbaseContainer.start();

        Cluster cluster = Cluster.connect(
                couchbaseContainer.getConnectionString(),
                couchbaseContainer.getUsername(),
                couchbaseContainer.getPassword()
        );

        Bucket bucket = cluster.bucket(testBucketDefinition.getName());
        bucket.waitUntilReady(Duration.ofSeconds(30));

        return new CouchbaseEmbeddingStore.Builder(couchbaseContainer.getConnectionString())
                .username(couchbaseContainer.getUsername())
                .password(couchbaseContainer.getPassword())
                .bucketName(testBucketDefinition.getName())
                .scopeName("_default")
                .collectionName("_default")
                .searchIndexName("test")
                .dimensions(TEST_DIMENSIONS)
                .build();
    });

    static final Supplier<EmbeddingModel> embeddingModel = Suppliers.memoize(AllMiniLmL6V2QuantizedEmbeddingModel::new);
}
