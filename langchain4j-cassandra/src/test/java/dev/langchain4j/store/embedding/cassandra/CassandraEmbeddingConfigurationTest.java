package dev.langchain4j.store.embedding.cassandra;

import org.junit.jupiter.api.Test;

import static dev.langchain4j.store.embedding.cassandra.CassandraEmbeddingConfiguration.DEFAULT_PORT;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;

public class CassandraEmbeddingConfigurationTest {

    @Test
    public void should_build_configuration_test() {
        CassandraEmbeddingConfiguration config = CassandraEmbeddingConfiguration.builder()
                .contactPoints(singletonList("localhost"))
                .port(DEFAULT_PORT)
                .keyspace("ks")
                .dimension(20)
                .table("table")
                .localDataCenter("dc1")
                .build();
        assertNotNull(config);
    }

    @Test
    public void should_error_if_no_datacenter_test() {
        // Table is required
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> CassandraEmbeddingConfiguration.builder()
                        .contactPoints(singletonList("localhost"))
                        .port(DEFAULT_PORT)
                        .keyspace("ks")
                        .dimension(20)
                        .table("table")
                        .build());
        assertEquals("localDataCenter is marked non-null but is null", exception.getMessage());
    }

    @Test
    public void should_error_if_no_table_test() {
        // Table is required
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> CassandraEmbeddingConfiguration.builder()
                        .contactPoints(singletonList("localhost"))
                        .port(DEFAULT_PORT)
                        .keyspace("ks")
                        .dimension(20)
                        .localDataCenter("dc1")
                        .build());
        assertEquals("table is marked non-null but is null", exception.getMessage());
    }

    @Test
    public void should_error_if_no_keyspace_test() {
        // Table is required
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> CassandraEmbeddingConfiguration.builder()
                        .contactPoints(singletonList("localhost"))
                        .port(DEFAULT_PORT)
                        .table("ks")
                        .dimension(20)
                        .localDataCenter("dc1")
                        .build());
        assertEquals("keyspace is marked non-null but is null", exception.getMessage());
    }

    @Test
    public void should_error_if_no_dimension_test() {
        // Table is required
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> CassandraEmbeddingConfiguration.builder()
                        .contactPoints(singletonList("localhost"))
                        .port(DEFAULT_PORT)
                        .table("ks")
                        .keyspace("ks")
                        .localDataCenter("dc1")
                        .build());
        assertEquals("dimension is marked non-null but is null", exception.getMessage());
    }

    @Test
    public void should_error_if_no_contact_points_test() {
        // Table is required
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> CassandraEmbeddingConfiguration.builder()
                        .port(DEFAULT_PORT)
                        .table("ks")
                        .keyspace("ks")
                        .dimension(20)
                        .localDataCenter("dc1")
                        .build());
        assertEquals("contactPoints is marked non-null but is null", exception.getMessage());
    }
}
