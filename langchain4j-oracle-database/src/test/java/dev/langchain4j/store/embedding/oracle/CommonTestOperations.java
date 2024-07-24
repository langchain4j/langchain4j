package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import oracle.jdbc.datasource.OracleDataSource;
import oracle.sql.CHAR;
import oracle.sql.CharacterSet;
import org.testcontainers.oracle.OracleContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;

/**
 * A collection of operations which are shared by tests in this package.
 */
final class CommonTestOperations {

    /**
     * Model used to generate embeddings for this test. The all-MiniLM-L6-v2 model is chosen for consistency with other
     * implementations of EmbeddingStoreIT.
     */
    private static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();

    /**
     * Seed for random numbers. When a test fails, "-Ddev.langchain4j.store.embedding.oracle.SEED=..." can be used to
     * re-execute it with the same random numbers.
     */
    private static final long SEED = Long.getLong(
            "dev.langchain4j.store.embedding.oracle.SEED", System.currentTimeMillis());

    /**
     * Used to generate random numbers, such as those for an embedding vector.
     */
    private static final Random RANDOM = new Random(SEED);

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

    /**
     * Returns the character set of the database. This can be used in {@link org.junit.jupiter.api.Assumptions} that
     * require a unicode character set.
     */
    static CharacterSet getCharacterSet() throws SQLException {
        try (Connection connection = CommonTestOperations.getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 'c' FROM sys.dual")) {
            resultSet.next();
            return resultSet.getObject(1, CHAR.class).getCharacterSet();
        }
    }

    /**
     * Returns an array of random floats, which can be used to generate test embedding vectors.
     *
     * @param length Array length.
     * @return Array of random floats. Not null.
     */
    static float[] randomFloats(int length) {
        float[] floats = new float[length];

        for (int i = 0; i < floats.length; i++)
            floats[i] = RANDOM.nextFloat();

        return floats;
    }
}
