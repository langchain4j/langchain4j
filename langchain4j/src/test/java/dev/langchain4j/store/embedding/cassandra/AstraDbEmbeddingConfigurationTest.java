package dev.langchain4j.store.embedding.cassandra;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The configuration objects include a few validation rules.
 */
public class AstraDbEmbeddingConfigurationTest {

    @Test
    public void should_build_configuration_test() {
        AstraDbEmbeddingConfiguration config = AstraDbEmbeddingConfiguration.builder()
                .token("token").databaseId("dbId").databaseRegion("dbRegion")
                .keyspace("ks").dimension(20).table("table")
                .build();
        Assertions.assertNotNull(config);
        Assertions.assertNotNull(config.getToken());
        Assertions.assertNotNull(config.getDatabaseId());
        Assertions.assertNotNull(config.getDatabaseRegion());
    }
    @Test
    public void should_error_if_no_table_test() {
        // Table is required
        NullPointerException exception = Assertions.assertThrows(NullPointerException.class,
            () -> AstraDbEmbeddingConfiguration.builder()
                    .token("token").databaseId("dbId").databaseRegion("dbRegion")
                    .keyspace("ks").dimension(20)
                    .build());
        Assertions. assertEquals("table is marked non-null but is null", exception.getMessage());
    }

    @Test
    public void should_error_if_no_keyspace_test() {
        // Table is required
        NullPointerException exception = Assertions.assertThrows(NullPointerException.class,
                () -> AstraDbEmbeddingConfiguration.builder()
                        .token("token").databaseId("dbId").databaseRegion("dbRegion")
                        .table("ks").dimension(20)
                        .build());
        Assertions. assertEquals("keyspace is marked non-null but is null", exception.getMessage());
    }

    @Test
    public void should_error_if_no_dimension_test() {
        // Table is required
        NullPointerException exception = Assertions.assertThrows(NullPointerException.class,
                () -> AstraDbEmbeddingConfiguration.builder()
                        .token("token").databaseId("dbId").databaseRegion("dbRegion")
                        .table("ks").keyspace("ks")
                        .build());
        Assertions. assertEquals("dimension is marked non-null but is null", exception.getMessage());
    }

    @Test
    public void should_error_if_no_token_test() {
        // Table is required
        NullPointerException exception = Assertions.assertThrows(NullPointerException.class,
                () -> AstraDbEmbeddingConfiguration.builder()
                        .databaseId("dbId").databaseRegion("dbRegion")
                        .table("ks").keyspace("ks").dimension(20)
                        .build());
        Assertions. assertEquals("token is marked non-null but is null", exception.getMessage());
    }

    @Test
    public void should_error_if_no_database_test() {
        // Table is required
        NullPointerException exception = Assertions.assertThrows(NullPointerException.class,
                () -> AstraDbEmbeddingConfiguration.builder()
                        .token("token")
                        .table("ks").keyspace("ks").dimension(20)
                        .build());
        Assertions. assertEquals("databaseId is marked non-null but is null", exception.getMessage());
    }




}
