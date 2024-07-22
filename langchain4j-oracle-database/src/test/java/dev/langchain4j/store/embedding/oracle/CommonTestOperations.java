package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import oracle.jdbc.datasource.OracleDataSource;
import org.testcontainers.oracle.OracleContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A collection of operations which are shared by tests in this package.
 */
final class CommonTestOperations {

    /**
     * Model used to generate embeddings for this test. The all-MiniLM-L6-v2 model is chosen for consistency with other
     * implementations of EmbeddingStoreIT.
     */
    private static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private CommonTestOperations() {}

    private static final DataSource DATA_SOURCE;
    static {
        try {
            OracleDataSource oracleDataSource = new oracle.jdbc.datasource.impl.OracleDataSource();
            String urlFromEnv = System.getenv("ORACLE_JDBC_URL");

            if (urlFromEnv == null) {
                // The Ryuk component is relied upon to stop this container.
                OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.4-slim-faststart")
                    .withDatabaseName("pdb1")
                    .withUsername("testuser")
                    .withPassword("testpwd");
                oracleContainer.start();

                oracleDataSource.setURL(oracleContainer.getJdbcUrl());
                oracleDataSource.setUser(oracleContainer.getUsername());
                oracleDataSource.setPassword(oracleContainer.getPassword());
            }
            else {
                oracleDataSource.setURL(urlFromEnv);
                oracleDataSource.setUser(System.getenv("ORACLE_JDBC_USER"));
                oracleDataSource.setPassword(System.getenv("ORACLE_JDBC_PASSWORD"));
            }

            DATA_SOURCE = new TestDataSource(oracleDataSource);
        } catch (
                SQLException sqlException) {
            throw new AssertionError(sqlException);
        }
    }

    static EmbeddingModel getEmbeddingModel() {
        return EMBEDDING_MODEL;
    }

    static DataSource getDataSource() {
        return DATA_SOURCE;
    }

    /**
     * Drops the table and index created by an embedding store. Tests can call this method in an
     * {@link org.junit.jupiter.api.AfterAll} method to clean up.
     *
     * @param tableName Name of table to drop. Not null.
     *
     * @throws SQLException If a database error prevents the drop.
     */
    static void dropTable(String tableName) throws SQLException {
        try (Connection connection = DATA_SOURCE.getConnection();
             Statement statement = connection.createStatement()) {
            statement.addBatch("DROP INDEX IF EXISTS " + tableName + "_embedding_index");
            statement.addBatch("DROP TABLE IF EXISTS " + tableName);
            statement.executeBatch();
        }
    }
}
