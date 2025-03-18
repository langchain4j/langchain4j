package dev.langchain4j.engine;

import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.alloydb.ConnectorConfig;
import com.google.cloud.alloydb.ConnectorRegistry;
import com.google.cloud.alloydb.RefreshStrategy;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pgvector.PGvector;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AlloyDBEngine
 * <p>
 * Instances of this store are created by configuring a builder:
 * </p>{@code
 * AlloyDBEngine engine = new AlloyDBEngine.Builder(projectId, region, cluster, instance, database).build();
 * }
 * Uses HikariCP as a DataSource. A connection pool that will avoid the latency of repeatedly creating new database connections.
 */
public class AlloyDBEngine {

    private static final String USER_AGENT = "langchain4j-alloydb-pg";
    private static final Logger log = LoggerFactory.getLogger(AlloyDBEngine.class.getName());
    private static ConnectorConfig namedConnectorConfig;
    private final HikariDataSource dataSource;

    /**
     * Constructor for AlloyDBEngine
     *
     * @param builder builder.
     */
    public AlloyDBEngine(Builder builder) {
        if (isNotNullOrBlank(builder.host)
                && (isNotNullOrBlank(builder.projectId) || isNotNullOrBlank(builder.cluster))) {
            throw new IllegalStateException(
                    "Connect directly to an instance using projectId, region, cluster, instance, and database params or connect via an IP Address using host, user, password, and database params");
        }

        if (isNotNullOrBlank(builder.cluster)) {
            Boolean enableIAMAuth;
            String authId = builder.user;
            if (isNullOrBlank(authId) && isNullOrBlank(builder.password)) {
                enableIAMAuth = true;
                if (isNotNullOrBlank(builder.iamAccountEmail)) {
                    log.debug("Found iamAccountEmail");
                    authId = builder.iamAccountEmail;
                } else {
                    log.debug("Retrieving IAM principal email");
                    authId = getIAMPrincipalEmail().replace(".gserviceaccount.com", "");
                }
            } else if (isNotNullOrBlank(authId) && isNotNullOrBlank(builder.password)) {
                enableIAMAuth = false;
                log.debug("Found user and password, IAM Auth disabled");
            } else {
                throw new IllegalStateException(
                        "Either one of user or password is blank, expected both user and password to be valid credentials or empty");
            }
            String instanceName = new StringBuilder("projects/")
                    .append(ensureNotBlank(builder.projectId, "projectId"))
                    .append("/locations/")
                    .append(ensureNotBlank(builder.region, "region"))
                    .append("/clusters/")
                    .append(ensureNotBlank(builder.cluster, "cluster"))
                    .append("/instances/")
                    .append(ensureNotBlank(builder.instance, "instance"))
                    .toString();
            dataSource = createConnectorDataSource(
                    builder.database, authId, builder.password, instanceName, builder.ipType, enableIAMAuth);
        } else {
            dataSource =
                    createUrlDataSource(builder.database, builder.user, builder.password, builder.host, builder.port);
        }
    }

    private HikariDataSource createConnectorDataSource(
            String database, String user, String password, String instanceName, String ipType, Boolean enableIAMAuth) {
        if (namedConnectorConfig == null) {
            namedConnectorConfig = new ConnectorConfig.Builder()
                    .withRefreshStrategy(RefreshStrategy.LAZY)
                    .build();
            ConnectorRegistry.addArtifactId(USER_AGENT);
            ConnectorRegistry.register("langchain-connector", namedConnectorConfig);
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format("jdbc:postgresql:///%s", ensureNotBlank(database, "database")));
        config.setUsername(ensureNotBlank(user, "user"));
        if (enableIAMAuth) {
            config.addDataSourceProperty("alloydbEnableIAMAuth", "true");
        } else {
            config.setPassword(ensureNotBlank(password, "password"));
        }
        config.addDataSourceProperty("socketFactory", "com.google.cloud.alloydb.SocketFactory");
        config.addDataSourceProperty("alloydbInstanceName", ensureNotBlank(instanceName, "instanceName"));
        config.addDataSourceProperty("alloydbIpType", ensureNotBlank(ipType, "ipType"));
        config.addDataSourceProperty("alloydbNamedConnector", "langchain-connector");
        return new HikariDataSource(config);
    }

    private HikariDataSource createUrlDataSource(
            String database, String user, String password, String host, Integer port) {
        HikariConfig config = new HikariConfig();
        // The following URL is equivalent to setting the config options below:
        // jdbc:postgresql://<INSTANCE_HOST>:<DB_PORT>/<DB_NAME>?user=<DB_USER>&password=<DB_PASS>

        // Configure which instance and what database user to connect with.
        config.setJdbcUrl(String.format(
                "jdbc:postgresql://%s:%d/%s",
                ensureNotBlank(host, "host"), port, ensureNotBlank(database, "database")));
        config.setUsername(ensureNotBlank(user, "user"));
        config.setPassword(ensureNotBlank(password, "password"));
        return new HikariDataSource(config);
    }

    private String getIAMPrincipalEmail() {
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            String accessToken = credentials.refreshAccessToken().getTokenValue();

            String oauth2APIURL = "https://oauth2.googleapis.com/tokeninfo?access_token=" + accessToken;
            byte[] responseBytes = readBytes(oauth2APIURL);
            JsonObject responseJson =
                    JsonParser.parseString(new String(responseBytes)).getAsJsonObject();
            if (responseJson.has("email")) {
                return responseJson.get("email").getAsString();
            } else {
                throw new RuntimeException("unable to load IAM principal email");
            }
        } catch (IOException e) {
            throw new RuntimeException("unable to load IAM principal email", e);
        }
    }

    /**
     * Gets a Connection from the datasource
     * @return A connection with the database specified in {@link AlloyDBEngine}
     * @throws SQLException if database error occurs
     */
    public Connection getConnection() throws SQLException {
        Connection connection = dataSource.getConnection();
        return connection;
    }

    /**
     * @param embeddingStoreConfig contains the parameters necesary to intialize
     * the Vector table
     */
    public void initVectorStoreTable(EmbeddingStoreConfig embeddingStoreConfig) {
        try (Connection connection = getConnection(); ) {
            Statement statement = connection.createStatement();
            statement.executeUpdate("CREATE EXTENSION IF NOT EXISTS vector");
            PGvector.addVectorType(connection);

            if (embeddingStoreConfig.getOverwriteExisting()) {
                statement.executeUpdate(String.format(
                        "DROP TABLE IF EXISTS \"%s\".\"%s\"",
                        embeddingStoreConfig.getSchemaName(), embeddingStoreConfig.getTableName()));
            }
            String metadataClause = "";
            if (embeddingStoreConfig.getMetadataColumns() != null
                    && !embeddingStoreConfig.getMetadataColumns().isEmpty()) {
                metadataClause += String.format(
                        ", %s",
                        embeddingStoreConfig.getMetadataColumns().stream()
                                .map(MetadataColumn::generateColumnString)
                                .collect(Collectors.joining(", ")));
            }
            if (embeddingStoreConfig.getStoreMetadata()) {
                metadataClause += String.format(
                        ", %s",
                        new MetadataColumn(embeddingStoreConfig.getMetadataJsonColumn(), "JSON", true)
                                .generateColumnString());
            }
            String query = String.format(
                    "CREATE TABLE \"%s\".\"%s\" (\"%s\" UUID PRIMARY KEY, \"%s\" TEXT NULL, \"%s\" vector(%d) NOT NULL%s)",
                    embeddingStoreConfig.getSchemaName(),
                    embeddingStoreConfig.getTableName(),
                    embeddingStoreConfig.getIdColumn(),
                    embeddingStoreConfig.getContentColumn(),
                    embeddingStoreConfig.getEmbeddingColumn(),
                    embeddingStoreConfig.getVectorSize(),
                    metadataClause);
            statement.executeUpdate(query);
        } catch (SQLException ex) {
            throw new RuntimeException(
                    String.format(
                            "Failed to initialize vector store table: \"%s\".\"%s\"",
                            embeddingStoreConfig.getSchemaName(), embeddingStoreConfig.getTableName()),
                    ex);
        }
    }

    /**
     * Closes a Connection
     */
    public void close() {
        dataSource.close();
    }

    /**
     * Builder which configures and creates instances of {@link AlloyDBEngine}.
     * Connect directly to an instance using projectId, region, cluster, instance, and database params
     * (Optional: user/password, iamAccountEmail, ipType)
     * or connect via an IP Address using host, user, password, and database params
     * (Optional: port)
     */
    public static class Builder {

        private String projectId;
        private String region;
        private String cluster;
        private String instance;
        private String database;
        private String host;
        private Integer port = 5432;
        private String user;
        private String password;
        private String ipType = "public";
        private String iamAccountEmail;

        /**
         * Creates a new {@code Builder} instance.
         */
        public Builder() {}

        /**
         * @param projectId (Optional) AlloyDB database projectId
         * @return this builder
         */
        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        /**
         * @param instance (Optional) AlloyDB database instance
         * @return this builder
         */
        public Builder instance(String instance) {
            this.instance = instance;
            return this;
        }

        /**
         * @param region (Optional) AlloyDB database region
         * @return this builder
         */
        public Builder region(String region) {
            this.region = region;
            return this;
        }

        /**
         * @param cluster (Optional) AlloyDB database cluster
         * @return this builder
         */
        public Builder cluster(String cluster) {
            this.cluster = cluster;
            return this;
        }

        /**
         * @param database (Optional) AlloyDB database database
         * @return this builder
         */
        public Builder database(String database) {
            this.database = database;
            return this;
        }

        /**
         * @param user (Optional) AlloyDB database user
         * @return this builder
         */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * @param password (Optional) AlloyDB database password
         * @return this builder
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * @param ipType (Optional) type of IP to be used (PUBLIC, PSC)
         * @return this builder
         */
        public Builder ipType(String ipType) {
            this.ipType = ipType;
            return this;
        }

        /**
         * @param iamAccountEmail (Optional) IAM account email
         * @return this builder
         */
        public Builder iamAccountEmail(String iamAccountEmail) {
            this.iamAccountEmail = iamAccountEmail;
            return this;
        }

        /**
         * @param host (Optional) AlloyDB database host
         * @return this builder
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * @param port (Optional) AlloyDB database port
         * @return this builder
         */
        public Builder port(Integer port) {
            this.port = port;
            return this;
        }

        /**
         * Builds an {@link AlloyDBEngine} store with the configuration applied to this builder.
         * @return A new {@link AlloyDBEngine} instance
         */
        public AlloyDBEngine build() {
            return new AlloyDBEngine(this);
        }
    }
}
