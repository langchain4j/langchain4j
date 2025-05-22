package dev.langchain4j.store.embedding.oceanbase;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Common operations for tests.
 */
public class CommonTestOperations {

    private static final Logger log = LoggerFactory.getLogger(CommonTestOperations.class);

    private static final String TABLE_NAME = "langchain4j_oceanbase_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final int VECTOR_DIM = 3;
    
    // OceanBase container configuration
    private static final int OCEANBASE_PORT = 2881; // Default OceanBase port
    private static GenericContainer<?> oceanBaseContainer;
    private static DataSource dataSource;
    private static EmbeddingModel embeddingModel;
    
    static {
        startOceanBaseContainer();
    }
    
    /**
     * Starts the OceanBase container for tests.
     */
    private static void startOceanBaseContainer() {
        try {
            // Using OceanBase's official Docker image
            oceanBaseContainer = new GenericContainer<>("oceanbase/oceanbase-ce:4.3.5-lts")
                .withExposedPorts(OCEANBASE_PORT)
                .withEnv("MODE", "standalone") // For single-node deployment
                // Wait for boot success message
                .waitingFor(Wait.forLogMessage(".*boot success!.*", 1));
                
            // Start the container
            oceanBaseContainer.start();
            
            // Get the mapped port and host
            String jdbcUrl = String.format("jdbc:oceanbase://%s:%d/test",
                oceanBaseContainer.getHost(),
                oceanBaseContainer.getMappedPort(OCEANBASE_PORT));
                
            log.info("OceanBase container started at {}", jdbcUrl);
            
            // Create a data source with the container's connection info
            dataSource = new SimpleDataSource(jdbcUrl, "root@test", "");
            
            // Create test database and setup
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                // Set memory limit for vector columns
                stmt.execute("ALTER SYSTEM SET ob_vector_memory_limit_percentage = 30;");
                // Create a test database if needed
                stmt.execute("CREATE DATABASE IF NOT EXISTS test_example");
                stmt.execute("USE test_example");
                // Add any other initialization SQL you need
            } catch (SQLException e) {
                log.error("Failed to initialize OceanBase container", e);
                throw new RuntimeException("Failed to initialize OceanBase container", e);
            }
        } catch (Exception e) {
            log.error("Failed to start OceanBase container", e);
            throw new RuntimeException("Failed to start OceanBase container", e);
        }
    }

    /**
     * Creates a new embedding store for testing.
     *
     * @return A new embedding store.
     */
    public static OceanBaseEmbeddingStore newEmbeddingStore() {
        return OceanBaseEmbeddingStore.builder(getDataSource())
                .embeddingTable(
                        EmbeddingTable.builder(TABLE_NAME)
                                .vectorDimension(VECTOR_DIM)
                                .createOption(CreateOption.CREATE_OR_REPLACE)
                                .build())
                .build();
    }

    /**
     * @return The embedding model.
     */
    public static EmbeddingModel getEmbeddingModel() {
        if (embeddingModel == null) {
            embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        }
        return embeddingModel;
    }

    /**
     * Returns the data source for connecting to OceanBase.
     *
     * @return The data source.
     */
    public static DataSource getDataSource() {
        if (dataSource == null) {
            throw new IllegalStateException("DataSource is not initialized. Container might not have started properly.");
        }
        return dataSource;
    }

    /**
     * Simple DataSource implementation for tests.
     */
    private static class SimpleDataSource implements DataSource {
        private final String url;
        private final String user;
        private final String password;

        public SimpleDataSource(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, user, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public java.io.PrintWriter getLogWriter() throws SQLException {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) throws SQLException {
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return null;
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Unwrapping not supported");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return false;
        }
    }

    /**
     * Drops the test table.
     *
     * @throws SQLException If an error occurs.
     */
    public static void dropTable() throws SQLException {
        try (Connection connection = getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
            log.info("Dropped table {}", TABLE_NAME);
        }
    }
    
    /**
     * Stops the OceanBase container.
     */
    public static void stopContainer() {
        if (oceanBaseContainer != null && oceanBaseContainer.isRunning()) {
            oceanBaseContainer.stop();
            log.info("OceanBase container stopped");
        }
    }
}
