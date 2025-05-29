package dev.langchain4j.data.document.loader;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

public class OracleContainerTestBase {
    // NB: need to use the regular image as the slim image doesn't include dbms_vector
    private static final String ORACLE_IMAGE_NAME = "gvenzl/oracle-free:23.6-faststart";
    private static final PoolDataSource DATA_SOURCE = PoolDataSourceFactory.getPoolDataSource();
    private static final PoolDataSource SYSDBA_DATA_SOURCE = PoolDataSourceFactory.getPoolDataSource();

    static OracleContainer container;

    @BeforeAll
    static void beforeAll() throws SQLException {
        if (System.getenv("ORACLE_JDBC_URL") == null) {
            container = new OracleContainer(ORACLE_IMAGE_NAME)
                    .withStartupTimeout(Duration.ofSeconds(600))
                    .withConnectTimeoutSeconds(600)
                    .withDatabaseName("pdb1")
                    .withUsername("testuser")
                    .withPassword("testpwd");
            container.start();

            initDataSource(DATA_SOURCE, container.getJdbcUrl(), container.getUsername(), container.getPassword());
            initDataSource(SYSDBA_DATA_SOURCE, container.getJdbcUrl(), "sys as sysdba", container.getPassword());
        } else {
            initDataSource(
                    DATA_SOURCE,
                    System.getenv("ORACLE_JDBC_URL"),
                    System.getenv("ORACLE_JDBC_USER"),
                    System.getenv("ORACLE_JDBC_PASSWORD"));
            initDataSource(
                    SYSDBA_DATA_SOURCE,
                    System.getenv("ORACLE_JDBC_URL"),
                    System.getenv("ORACLE_JDBC_USER"),
                    System.getenv("ORACLE_JDBC_PASSWORD"));
        }
    }

    @AfterAll
    static void afterAll() {
        if (container != null) container.stop();
    }

    static void initDataSource(PoolDataSource dataSource, String url, String username, String password) {
        try {
            dataSource.setConnectionFactoryClassName("oracle.jdbc.datasource.impl.OracleDataSource");
            dataSource.setURL(url);
            dataSource.setUser(username);
            dataSource.setPassword(password);
        } catch (SQLException sqlException) {
            throw new AssertionError(sqlException);
        }
    }

    public boolean isContainerRunning() {
        return container != null;
    }

    public Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    public Connection getSysConnection() throws SQLException {
        return SYSDBA_DATA_SOURCE.getConnection();
    }

    public PoolDataSource getDataSource() {
        return DATA_SOURCE;
    }

    public String getUsername() {
        if (container != null) return container.getUsername();
        else return System.getenv("ORACLE_JDBC_USER");
    }

    public void copyResourceFile(String resourcePath, String mountPath) {
        MountableFile file = MountableFile.forClasspathResource(resourcePath);
        container.copyFileToContainer(file, mountPath);
    }
}
