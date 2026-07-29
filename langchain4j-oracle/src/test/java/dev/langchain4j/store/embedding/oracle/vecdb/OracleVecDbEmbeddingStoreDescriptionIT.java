package dev.langchain4j.store.embedding.oracle.vecdb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.store.embedding.oracle.CreateOption;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbDistanceMetric;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the VecDB-specific vector-table description API.
 */
@Testcontainers(disabledWithoutDocker = true)
class OracleVecDbEmbeddingStoreDescriptionIT {

    private static final String TABLE_NAME = "LC4J_VECDB_DESCRIBE_IT";
    private static final String TABLE_COMMENT = "LangChain4j VecDB description integration test";

    private static OracleVecDbEmbeddingStore embeddingStore;

    @BeforeAll
    static void createVectorTable() {
        VecDbEmbeddingTable embeddingTable = VecDbEmbeddingTable.builder()
                .name(TABLE_NAME)
                .comment(TABLE_COMMENT)
                .annotation("application", "langchain4j")
                .annotation("environment", "integration-test")
                .createOption(CreateOption.CREATE_OR_REPLACE)
                .build();

        embeddingStore = OracleVecDbEmbeddingStore.builder()
                .dataSource(VecDbTestOperations.dataSource())
                .embeddingTable(embeddingTable)
                .distanceMetric(VecDbDistanceMetric.COSINE)
                .build();
    }

    @Test
    void testDescribeVectorTable() {
        OracleVecDbEmbeddingStore.VectorTableDescription description =
                embeddingStore.describeVectorTable();

        assertThat(description.tableName()).isEqualToIgnoringCase(TABLE_NAME);
        assertThat(description.comment()).isEqualTo(TABLE_COMMENT);
        assertThat(description.annotations())
                .containsEntry("application", "langchain4j")
                .containsEntry("environment", "integration-test");
    }

    @AfterAll
    static void dropVectorTable() throws SQLException {
        VecDbTestOperations.dropVectorTable(TABLE_NAME);
    }
}
