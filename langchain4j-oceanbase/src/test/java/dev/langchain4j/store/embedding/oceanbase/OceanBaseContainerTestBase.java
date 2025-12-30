package dev.langchain4j.store.embedding.oceanbase;

import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.oceanbase.OceanBaseCEContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.DockerLoggerFactory;

import java.time.Duration;

/**
 * Base class for OceanBase integration tests.
 * <p>
 * This class provides Testcontainers support with environment variable fallback.
 * If environment variables are set, it uses an external OceanBase instance.
 * Otherwise, it starts a Testcontainers container.
 */
public class OceanBaseContainerTestBase {

    private static final String OCEANBASE_IMAGE = "oceanbase/oceanbase-ce:latest";
    private static final int OCEANBASE_PORT = 2881;
    private static final String DEFAULT_USERNAME = "root@test";
    private static final String DEFAULT_PASSWORD = "";
    private static final String DEFAULT_DATABASE = "test";

    static OceanBaseCEContainer container;

    /**
     * Gets the JDBC URI for connecting to OceanBase.
     * Uses container if available, otherwise uses environment variables.
     */
    public static String getJdbcUrl() {
        String uriFromEnv = System.getenv("OCEANBASE_URI");
        if (uriFromEnv != null) {
            return uriFromEnv;
        }
        if (container != null && container.isRunning()) {
            return "jdbc:oceanbase://" + container.getHost() + ":" + container.getMappedPort(OCEANBASE_PORT) + "/" + DEFAULT_DATABASE;
        }
        throw new IllegalStateException("Neither OCEANBASE_URI environment variable is set nor container is running");
    }

    /**
     * Gets the username for connecting to OceanBase.
     */
    public static String getUsername() {
        String userFromEnv = System.getenv("OCEANBASE_USER");
        if (userFromEnv != null) {
            return userFromEnv;
        }
        return DEFAULT_USERNAME;
    }

    /**
     * Gets the password for connecting to OceanBase.
     */
    public static String getPassword() {
        String passwordFromEnv = System.getenv("OCEANBASE_PASSWORD");
        if (passwordFromEnv != null) {
            return passwordFromEnv;
        }
        return DEFAULT_PASSWORD;
    }

    /**
     * Initializes the OceanBase container if environment variables are not set.
     * Should be called in @BeforeAll method.
     */
    @SuppressWarnings("resource")
    public static void initContainer() {
        String uriFromEnv = System.getenv("OCEANBASE_URI");
        if (uriFromEnv == null) {
            container = new OceanBaseCEContainer(DockerImageName.parse(OCEANBASE_IMAGE))
                    .withEnv("MODE", "slim")
                    .withEnv("OB_DATAFILE_SIZE", "2G")  // Reduced from 2G to speed up startup
                    .withExposedPorts(OCEANBASE_PORT)
                    .withStartupTimeout(Duration.ofMinutes(3))  // Reduced from 5 minutes
                    .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forLogMessage(".*boot success!.*", 1))
                    .withReuse(true)  // Enable container reuse to speed up tests
                    .withLogConsumer(new Slf4jLogConsumer(DockerLoggerFactory.getLogger(OCEANBASE_IMAGE)));
            container.start();
        }
    }

    /**
     * Stops the container if it was started.
     * Should be called in @AfterAll method.
     */
    public static void stopContainer() {
        if (container != null) {
            try {
                container.stop();
            } finally {
                container.close();
                container = null;
            }
        }
    }

    /**
     * Checks if a container is running.
     */
    public static boolean isContainerRunning() {
        return container != null && container.isRunning();
    }
}

