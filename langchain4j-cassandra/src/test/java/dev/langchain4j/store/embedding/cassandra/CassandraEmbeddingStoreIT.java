package dev.langchain4j.store.embedding.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.dtsx.astra.sdk.cassio.SimilarityMetric;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static dev.langchain4j.internal.Utils.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Work with Cassandra Embedding Store.
 */
@Testcontainers
class CassandraEmbeddingStoreIT extends AbstractEmbeddingStoreTestSupport {

    static final String DATACENTER = "datacenter1";
    static CassandraContainer<?> cassandraContainer;

    @BeforeAll
    public static void ensureDockerIsRunning() {
        DockerClientFactory.instance().client();
    }

    @SuppressWarnings("resource")
    void createDatabase() {

        cassandraContainer = new CassandraContainer<>(DockerImageName.parse("cassandra:5.0"))
                .withEnv("CLUSTER_NAME", "langchain4j")
                .withEnv("DC", DATACENTER);
        /*
        final DockerImageName dseServerImage = DockerImageName
                .parse("datastax/dse-server:7.0.0-a")
                .asCompatibleSubstituteFor("cassandra");
        cassandraContainer = new CassandraContainer<>(dseServerImage)
                .withEnv("DS_LICENSE", "accept")
                .withEnv("CLUSTER_NAME", "langchain4j")
                .withEnv("DC", DATACENTER);*/

        cassandraContainer.start();
    }

    @Override
    CassandraEmbeddingStore createEmbeddingStore() {
        final InetSocketAddress contactPoint = cassandraContainer.getContactPoint();
        CqlSession.builder()
                .addContactPoint(contactPoint)
                .withLocalDatacenter(DATACENTER)
                .build().execute(
                        "CREATE KEYSPACE IF NOT EXISTS " + KEYSPACE +
                                " WITH replication = {'class':'SimpleStrategy', 'replication_factor':'1'};");
        return CassandraEmbeddingStore.builder()
                .contactPoints(Arrays.asList(contactPoint.getHostName()))
                .port(contactPoint.getPort())
                .localDataCenter(DATACENTER)
                .keyspace(KEYSPACE)
                .table(TEST_INDEX)
                .dimension(11)
                .metric(SimilarityMetric.COS)
                .build();
    }

    @AfterAll
    static void afterTests() throws Exception {
        cassandraContainer.stop();
    }

}
