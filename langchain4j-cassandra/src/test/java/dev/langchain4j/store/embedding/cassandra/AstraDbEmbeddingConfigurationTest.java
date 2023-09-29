package dev.langchain4j.store.embedding.cassandra;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The configuration objects include a few validation rules.
 */
public class AstraDbEmbeddingConfigurationTest {

    @Test
    public void should_build_configuration_test() {
        AstraDbEmbeddingConfiguration config = AstraDbEmbeddingConfiguration.builder()
                .token("token")
                .databaseId("dbId")
                .databaseRegion("dbRegion")
                .keyspace("ks")
                .dimension(20)
                .table("table")
                .build();
        assertNotNull(config);
        assertNotNull(config.getToken());
        assertNotNull(config.getDatabaseId());
        assertNotNull(config.getDatabaseRegion());
    }

    @Test
    public void should_error_if_no_table_test() {
        // Table is required
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> AstraDbEmbeddingConfiguration.builder()
                        .token("token")
                        .databaseId("dbId")
                        .databaseRegion("dbRegion")
                        .keyspace("ks")
                        .dimension(20)
                        .build());
        assertEquals("table is marked non-null but is null", exception.getMessage());
    }

    @Test
    public void should_error_if_no_keyspace_test() {
        // Table is required
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> AstraDbEmbeddingConfiguration.builder()
                        .token("token")
                        .databaseId("dbId")
                        .databaseRegion("dbRegion")
                        .table("ks")
                        .dimension(20)
                        .build());
        assertEquals("keyspace is marked non-null but is null", exception.getMessage());
    }

    @Test
    public void should_error_if_no_dimension_test() {
        // Table is required
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> AstraDbEmbeddingConfiguration.builder()
                        .token("token")
                        .databaseId("dbId")
                        .databaseRegion("dbRegion")
                        .table("ks")
                        .keyspace("ks")
                        .build());
        assertEquals("dimension is marked non-null but is null", exception.getMessage());
    }

    @Test
    public void should_error_if_no_token_test() {
        // Table is required
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> AstraDbEmbeddingConfiguration.builder()
                        .databaseId("dbId")
                        .databaseRegion("dbRegion")
                        .table("ks")
                        .keyspace("ks")
                        .dimension(20)
                        .build());
        assertEquals("token is marked non-null but is null", exception.getMessage());
    }

    @Test
    public void should_error_if_no_database_test() {
        // Table is required
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> AstraDbEmbeddingConfiguration.builder()
                        .token("token")
                        .table("ks")
                        .keyspace("ks")
                        .dimension(20)
                        .build());
        assertEquals("databaseId is marked non-null but is null", exception.getMessage());
    }
}
