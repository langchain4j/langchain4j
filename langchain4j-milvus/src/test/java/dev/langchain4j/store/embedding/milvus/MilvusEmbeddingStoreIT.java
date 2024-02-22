package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithoutMetadataIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.List;

import static dev.langchain4j.internal.Utils.randomUUID;
import static io.milvus.param.MetricType.IP;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

@Testcontainers
class MilvusEmbeddingStoreIT extends EmbeddingStoreWithoutMetadataIT {

    private static final Network network = Network.newNetwork();

    private static final MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2023-03-20T20-16-18Z")
            .withNetwork(network)
            .withNetworkAliases("minio");

    private static final GenericContainer<?> etcd = new GenericContainer<>("quay.io/coreos/etcd:v3.5.5")
            .withNetwork(network)
            .withNetworkAliases("etcd")
            .withCommand("etcd", "-advertise-client-urls=http://127.0.0.1:2379", "-listen-client-urls=http://0.0.0.0:2379",
                    "--data-dir=/etcd")
            .withEnv("ETCD_AUTO_COMPACTION_MODE", "revision")
            .withEnv("ETCD_AUTO_COMPACTION_RETENTION", "1000")
            .withEnv("ETCD_QUOTA_BACKEND_BYTES", "4294967296")
            .withEnv("ETCD_SNAPSHOT_COUNT", "50000")
            .waitingFor(Wait.forLogMessage(".*ready to serve client requests.*", 1));

    @Container
    private static final GenericContainer<?> milvus = new GenericContainer<>("milvusdb/milvus:v2.3.1")
            .withExposedPorts(19530)
            .dependsOn(minio, etcd)
            .withNetwork(network)
            .withCommand("milvus", "run", "standalone")
            .withEnv("ETCD_ENDPOINTS", "etcd:2379")
            .withEnv("MINIO_ADDRESS", "minio:9000")
            .waitingFor(Wait.forLogMessage(".*Proxy successfully started.*\\s", 1));

    EmbeddingStore<TextSegment> embeddingStore = MilvusEmbeddingStore.builder()
            .host(milvus.getHost())
            .port(milvus.getMappedPort(19530))
            .collectionName("collection_" + randomUUID().replace("-", ""))
            .dimension(384)
            .retrieveEmbeddingsOnSearch(true)
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

    @Test
    void should_not_retrieve_embeddings_when_searching() {

        EmbeddingStore<TextSegment> embeddingStore = MilvusEmbeddingStore.builder()
                .host(milvus.getHost())
                .port(milvus.getMappedPort(19530))
                .collectionName("collection_" + randomUUID().replace("-", ""))
                .dimension(384)
                .retrieveEmbeddingsOnSearch(false)
                .build();

        Embedding firstEmbedding = embeddingModel.embed("hello").content();
        Embedding secondEmbedding = embeddingModel.embed("hi").content();
        embeddingStore.addAll(asList(firstEmbedding, secondEmbedding));

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);
        assertThat(relevant.get(0).embedding()).isNull();
        assertThat(relevant.get(1).embedding()).isNull();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MILVUS_API_KEY", matches = ".+")
    void should_use_cloud_instance() {

        EmbeddingStore<TextSegment> embeddingStore = MilvusEmbeddingStore.builder()
                .uri("https://in03-d11858f677102da.api.gcp-us-west1.zillizcloud.com")
                .token(System.getenv("MILVUS_API_KEY"))
                .collectionName("test")
                .dimension(384)
                .metricType(IP) // COSINE is not supported at the moment
                .build();

        Embedding embedding = embeddingModel.embed(randomUUID()).content();

        String id = embeddingStore.add(embedding);
        assertThat(id).isNotNull();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isNull();
        assertThat(match.embedded()).isNull();
    }

    @Test
    void should_use_partition_searching() {
        String partitionName = "partition_" + randomUUID().replace("-", "");
        MilvusEmbeddingStore embeddingStore = MilvusEmbeddingStore.builder()
                .host(milvus.getHost())
                .port(milvus.getMappedPort(19530))
                .collectionName("collection_" + randomUUID().replace("-", ""))
                .dimension(384)
                .retrieveEmbeddingsOnSearch(false)
                .build();
        embeddingStore.createPartition(partitionName);

        Embedding firstEmbedding = embeddingModel.embed("hello").content();
        Embedding secondEmbedding = embeddingModel.embed("hi").content();
        embeddingStore.addAll(asList(firstEmbedding, secondEmbedding), null, partitionName);

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(firstEmbedding, 10, 0);
        assertThat(relevant).hasSize(2);
        assertThat(relevant.get(0).embedding()).isNull();
        assertThat(relevant.get(1).embedding()).isNull();

        List<EmbeddingMatch<TextSegment>> relevantByPartition = embeddingStore.findRelevant(firstEmbedding, 10, 0, Collections.singletonList(partitionName));
        assertThat(relevantByPartition).hasSize(2);
        assertThat(relevantByPartition.get(0).embedding()).isNull();
        assertThat(relevantByPartition.get(1).embedding()).isNull();
    }
}