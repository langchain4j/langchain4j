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

/**
 *
 * @author david
 */
public class OracleContainerTestBase {
    private static final String ORACLE_IMAGE_NAME = "gvenzl/oracle-free:23.6-faststart";
    private static final PoolDataSource DATA_SOURCE = PoolDataSourceFactory.getPoolDataSource();
    private static final PoolDataSource SYSDBA_DATA_SOURCE = PoolDataSourceFactory.getPoolDataSource();

    static OracleContainer container;

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

    @BeforeAll
    static void beforeAll() throws SQLException {
        container = new OracleContainer(ORACLE_IMAGE_NAME)
                .withStartupTimeout(Duration.ofSeconds(600))
                .withConnectTimeoutSeconds(600)
                .withDatabaseName("pdb1")
                .withUsername("testuser")
                .withPassword("testpwd");
        container.start();

        initDataSource(DATA_SOURCE, container.getJdbcUrl(), container.getUsername(), container.getPassword());
        initDataSource(SYSDBA_DATA_SOURCE, container.getJdbcUrl(), "sys as sysdba", container.getPassword());
    }

    @AfterAll
    static void afterAll() {
        container.stop();
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

    public void copyFile(String resourcePath, String mountPath) {
        MountableFile file = MountableFile.forClasspathResource(resourcePath);
        container.copyFileToContainer(file, mountPath);
    }
}
