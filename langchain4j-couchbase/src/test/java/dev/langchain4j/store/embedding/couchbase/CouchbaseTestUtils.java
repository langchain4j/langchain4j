package dev.langchain4j.store.embedding.couchbase;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

@Testcontainers
class CouchbaseTestUtils {

    private static final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    static EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    private static CouchbaseEmbeddingStore cloudStoreInstance;

    public static CouchbaseEmbeddingStore cloudStore() {
        if (cloudStoreInstance == null) {
            cloudStoreInstance = CouchbaseEmbeddingStore.builder()
                    .clusterUrl(System.getenv("COUCHBASE_CLUSTER_URL"))
                    .username(System.getenv("COUCHBASE_USERNAME"))
                    .password(System.getenv("COUCHBASE_PASSWORD"))
                    .bucketName(System.getenv("COUCHBASE_BUCKET"))
                    .scopeName(System.getenv("COUCHBASE_SCOPE"))
                    .collectionName(System.getenv("COUCHBASE_COLLECTION"))
                    .searchIndexName(System.getenv("COUCHBASE_FTS_INDEX"))
                    .dimensions(embeddingModel.dimension())
                    .build();
        }

        return cloudStoreInstance;
    }

    static BucketDefinition testBucketDefinition = new BucketDefinition("test")
            .withPrimaryIndex(true)
            .withQuota(100);

    @Container
    static final CouchbaseContainer couchbaseContainer =
            new CouchbaseContainer(DockerImageName.parse("couchbase:enterprise").asCompatibleSubstituteFor("couchbase/server"))
                    .withCredentials("Administrator", "password")
                    .withBucket(testBucketDefinition)
                    .withStartupTimeout(Duration.ofMinutes(1));

    private static CouchbaseEmbeddingStore containerStoreInstance;

    public static CouchbaseEmbeddingStore containerStore() {
        if (containerStoreInstance == null) {
            couchbaseContainer.start();

            Cluster cluster = Cluster.connect(
                    couchbaseContainer.getConnectionString(),
                    couchbaseContainer.getUsername(),
                    couchbaseContainer.getPassword()
            );

            Bucket bucket = cluster.bucket(testBucketDefinition.getName());
            bucket.waitUntilReady(Duration.ofSeconds(30));

            containerStoreInstance = CouchbaseEmbeddingStore.builder()
                    .clusterUrl(couchbaseContainer.getConnectionString())
                    .username(couchbaseContainer.getUsername())
                    .password(couchbaseContainer.getPassword())
                    .bucketName(testBucketDefinition.getName())
                    .scopeName("_default")
                    .collectionName("_default")
                    .searchIndexName("test")
                    .dimensions(embeddingModel().dimension())
                    .build();
        }
        return containerStoreInstance;
    }
}
