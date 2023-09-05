package dev.langchain4j.store.embedding.cassandra;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

public class CassandraEmbeddingConfigurationTest {

    @Test
    public void should_build_configuration_test() {
        CassandraEmbeddingConfiguration config = CassandraEmbeddingConfiguration.builder()
                .contactPoints(Collections.singletonList("localhost"))
                .port(CassandraEmbeddingConfiguration.DEFAULT_PORT)
                .keyspace("ks").dimension(20).table("table")
                .localDataCenter("dc1")
                .build();
        Assertions.assertNotNull(config);
    }

    @Test
    public void should_error_if_no_datacenter_test() {
        // Table is required
        NullPointerException exception = Assertions.assertThrows(NullPointerException.class,
                () -> CassandraEmbeddingConfiguration.builder()
                        .contactPoints(Collections.singletonList("localhost"))
                        .port(CassandraEmbeddingConfiguration.DEFAULT_PORT)
                        .keyspace("ks").dimension(20).table("table")
                        .build());
        Assertions. assertEquals("localDataCenter is marked non-null but is null", exception.getMessage());
    }

    @Test
    public void should_error_if_no_table_test() {
        // Table is required
        NullPointerException exception = Assertions.assertThrows(NullPointerException.class,
                () -> CassandraEmbeddingConfiguration.builder()
                        .contactPoints(Collections.singletonList("localhost"))
                        .port(CassandraEmbeddingConfiguration.DEFAULT_PORT)
                        .keyspace("ks").dimension(20)
                        .localDataCenter("dc1")
                        .build());
        Assertions. assertEquals("table is marked non-null but is null", exception.getMessage());
    }

    @Test
    public void should_error_if_no_keyspace_test() {
        // Table is required
        NullPointerException exception = Assertions.assertThrows(NullPointerException.class,
                () ->CassandraEmbeddingConfiguration.builder()
                        .contactPoints(Collections.singletonList("localhost"))
                        .port(CassandraEmbeddingConfiguration.DEFAULT_PORT)
                        .table("ks").dimension(20)
                        .localDataCenter("dc1")
                        .build());
        Assertions. assertEquals("keyspace is marked non-null but is null", exception.getMessage());
    }

    @Test
    public void should_error_if_no_dimension_test() {
        // Table is required
        NullPointerException exception = Assertions.assertThrows(NullPointerException.class,
                () -> CassandraEmbeddingConfiguration.builder()
                        .contactPoints(Collections.singletonList("localhost"))
                        .port(CassandraEmbeddingConfiguration.DEFAULT_PORT)
                        .table("ks").keyspace("ks")
                        .localDataCenter("dc1")
                        .build());
        Assertions. assertEquals("dimension is marked non-null but is null", exception.getMessage());
    }

    @Test
    public void should_error_if_no_contact_points_test() {
        // Table is required
        NullPointerException exception = Assertions.assertThrows(NullPointerException.class,
                () -> CassandraEmbeddingConfiguration.builder()
                        .port(CassandraEmbeddingConfiguration.DEFAULT_PORT)
                        .table("ks").keyspace("ks").dimension(20)
                        .localDataCenter("dc1")
                        .build());
        Assertions. assertEquals("contactPoints is marked non-null but is null", exception.getMessage());
    }

}
