package dev.langchain4j.store.memory.chat.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.InetSocketAddress;

/**
 * Test Cassandra Chat Memory Store with a Saas DB.
 */
@Testcontainers
class CassandraChatMemoryStoreDockerIT extends CassandraChatMemoryStoreTestSupport {
    static final String DATACENTER = "datacenter1";
    static final DockerImageName CASSANDRA_IMAGE = DockerImageName.parse("cassandra:5.0");
    static CassandraContainer<?> cassandraContainer;

    @BeforeAll
    public static void ensureDockerIsRunning() {
        DockerClientFactory.instance().client();
    }

    @Override
    @SuppressWarnings("resource")
    void createDatabase() {
        cassandraContainer = new CassandraContainer<>(CASSANDRA_IMAGE)
                .withEnv("CLUSTER_NAME", "langchain4j")
                .withEnv("DC", DATACENTER);
        cassandraContainer.start();
    }

    @Override
    @SuppressWarnings("resource")
    CassandraChatMemoryStore createChatMemoryStore() {
        final InetSocketAddress contactPoint =
                cassandraContainer.getContactPoint();
        CqlSession.builder()
                .addContactPoint(contactPoint)
                .withLocalDatacenter(DATACENTER)
                .build().execute(
                        "CREATE KEYSPACE IF NOT EXISTS " + KEYSPACE +
                                " WITH replication = {'class':'SimpleStrategy', 'replication_factor':'1'};");
        return new CassandraChatMemoryStore(CqlSession.builder()
                .addContactPoint(contactPoint)
                .withLocalDatacenter(DATACENTER)
                .withKeyspace(KEYSPACE)
                .build());
    }

    @AfterAll
    static void afterTests() {
        cassandraContainer.stop();
    }
}
