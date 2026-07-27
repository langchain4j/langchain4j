package dev.langchain4j.store.embedding.oracle.vecdb;

import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Device;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import javax.sql.DataSource;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Provides the shared Autonomous AI Database container and JDBC data source used by VecDB integration tests.
 *
 * <p>The container is started lazily and remains available for the lifetime of the test JVM. Testcontainers' Ryuk
 * process removes it when the JVM exits.
 */
final class VecDbTestEnvironment {

    private static final String CONNECTION_FACTORY_CLASS = "oracle.jdbc.datasource.impl.OracleDataSource";
    private static final String DEFAULT_IMAGE = "ghcr.io/oracle/adb-free:latest-26ai";
    private static final String DATABASE_NAME = "LANGCHAIN4J";
    private static final String ADMIN_PASSWORD = "Welcome_Vecdb_123";
    private static final String WALLET_PASSWORD = "Welcome_Vecdb_123";
    private static final String WALLET_CONTAINER_PATH = "/u01/app/oracle/wallets/tls_wallet";

    private static final GenericContainer<?> DATABASE = new GenericContainer<>(
                    DockerImageName.parse(System.getProperty("vecdb.test.image", DEFAULT_IMAGE)))
            .withEnv("WORKLOAD_TYPE", "ATP")
            .withEnv("DATABASE_NAME", DATABASE_NAME)
            .withEnv("ADMIN_PASSWORD", ADMIN_PASSWORD)
            .withEnv("WALLET_PASSWORD", WALLET_PASSWORD)
            .withEnv("ENABLE_ARCHIVE_LOG", "False")
            .withExposedPorts(1522)
            .withCreateContainerCmdModifier(command -> command
                    .getHostConfig()
                    .withCapAdd(Capability.SYS_ADMIN)
                    .withDevices(Device.parse("/dev/fuse:/dev/fuse:rwm")))
            .waitingFor(Wait.forHealthcheck().withStartupTimeout(Duration.ofMinutes(30)));

    private static final PoolDataSource DATA_SOURCE = startDatabase();

    private VecDbTestEnvironment() {}

    static PoolDataSource dataSource() {
        return DATA_SOURCE;
    }

    private static PoolDataSource startDatabase() {
        DATABASE.start();
        try {
            Path walletDirectory = Files.createTempDirectory("langchain4j-vecdb-wallet-");
            copyWallet(walletDirectory);
            updateWalletConnectionAddress(walletDirectory);
            configureJdbcWallet(walletDirectory);

            PoolDataSource poolDataSource = PoolDataSourceFactory.getPoolDataSource();
            poolDataSource.setConnectionFactoryClassName(CONNECTION_FACTORY_CLASS);
            poolDataSource.setURL("jdbc:oracle:thin:@langchain4j_low?TNS_ADMIN=" + walletDirectory.toAbsolutePath());
            poolDataSource.setUser("ADMIN");
            poolDataSource.setPassword(ADMIN_PASSWORD);
            poolDataSource.setInitialPoolSize(1);
            poolDataSource.setMinPoolSize(1);
            poolDataSource.setMaxPoolSize(5);
            awaitDatabase(poolDataSource);
            return poolDataSource;
        } catch (Exception exception) {
            DATABASE.stop();
            throw new IllegalStateException("Unable to initialize the VecDB test environment", exception);
        }
    }

    private static void copyWallet(Path walletDirectory) throws Exception {
        Files.createDirectories(walletDirectory);
        org.testcontainers.containers.Container.ExecResult walletFiles = DATABASE.execInContainer(
                "find", WALLET_CONTAINER_PATH, "-maxdepth", "1", "-type", "f", "-printf", "%f\n");
        if (walletFiles.getExitCode() != 0) {
            throw new IllegalStateException("Unable to list the generated ADB wallet: " + walletFiles.getStderr());
        }

        for (String fileName : walletFiles.getStdout().lines().filter(line -> !line.isBlank()).toList()) {
            DATABASE.copyFileFromContainer(
                    WALLET_CONTAINER_PATH + "/" + fileName,
                    walletDirectory.resolve(fileName).toString());
        }
    }

    private static void updateWalletConnectionAddress(Path walletDirectory) throws Exception {
        Path tnsNames = walletDirectory.resolve("tnsnames.ora");
        String configuredTnsNames = Files.readString(tnsNames)
                .replaceAll("(?i)(HOST\\s*=\\s*)[^)]+", "$1" + DATABASE.getHost())
                .replaceAll("(?i)(PORT\\s*=\\s*)1522", "$1" + DATABASE.getMappedPort(1522));
        Files.writeString(tnsNames, configuredTnsNames);
    }

    private static void configureJdbcWallet(Path walletDirectory) throws Exception {
        String wallet = walletDirectory.resolve("cwallet.sso").toAbsolutePath().toString();
        String jdbcProperties = """
                javax.net.ssl.keyStore=%s
                javax.net.ssl.keyStoreType=SSO
                javax.net.ssl.trustStore=%s
                javax.net.ssl.trustStoreType=SSO
                """
                .formatted(wallet, wallet);
        Files.writeString(walletDirectory.resolve("ojdbc.properties"), jdbcProperties);
    }

    private static void awaitDatabase(DataSource dataSource) throws Exception {
        long deadline = System.nanoTime() + Duration.ofMinutes(5).toNanos();
        SQLException lastFailure = null;

        while (System.nanoTime() < deadline) {
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT 1 FROM DUAL")) {
                if (resultSet.next()) {
                    return;
                }
            } catch (SQLException exception) {
                lastFailure = exception;
            }
            Thread.sleep(Duration.ofSeconds(5).toMillis());
        }

        throw new IllegalStateException("ADB container did not accept JDBC connections", lastFailure);
    }
}
