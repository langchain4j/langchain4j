package dev.langchain4j.store.embedding.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.dtsx.astra.sdk.cassio.SimilarityMetric;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.InetSocketAddress;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Work with Cassandra Embedding Store.
 */
@Testcontainers
class CassandraEmbeddingStoreDockerIT extends CassandraEmbeddingStoreTestSupport {

    static final String CASSANDRA_IMAGE = "cassandra:5.0";
    static final String DATACENTER = "datacenter1";
    static final String CLUSTER = "langchain4j";
    static CassandraContainer<?> cassandraContainer;

    /**
     * Check Docker is installed and running on host
     */
    @BeforeAll
    static void ensureDockerIsRunning() {
        DockerClientFactory.instance().client();
    }

    /**
     * Start Cassandra as a Docker Container
     */
    @Override
    @SuppressWarnings("resource")
    void createDatabase() {
        cassandraContainer = new CassandraContainer<>(
                DockerImageName.parse(CASSANDRA_IMAGE))
                .withEnv("CLUSTER_NAME", CLUSTER)
                .withEnv("DC", DATACENTER);
        cassandraContainer.start();
    }

    /**
     * Create the Keyspace.
     * Create the table.
     * @return
     *      embedding Store
     */
    @Override
    CassandraEmbeddingStore createEmbeddingStore() {
        final InetSocketAddress contactPoint = cassandraContainer.getContactPoint();
        // Create the keyspace
        CqlSession.builder()
                .addContactPoint(contactPoint)
                .withLocalDatacenter(DATACENTER)
                .build().execute(
                        "CREATE KEYSPACE IF NOT EXISTS " + KEYSPACE +
                                " WITH replication = {'class':'SimpleStrategy', 'replication_factor':'1'};");
        // Create the table
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

    /**
     * Stop Cassandra Node
     */
    @AfterAll
    static void afterTests() throws Exception {
        cassandraContainer.stop();
    }

}
