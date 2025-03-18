package dev.langchain4j.engine;

import static dev.langchain4j.internal.Utils.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.utils.AlloyDBTestUtils;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class AlloyDBEngineIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    private static final String TABLE_NAME = "java_engine_test_table" + randomUUID();
    private static final String CUSTOM_TABLE_NAME = "java_engine_test_custom_table" + randomUUID();
    private static final String CUSTOM_SCHEMA = "custom_schema";
    private static final Integer VECTOR_SIZE = 768;
    private static EmbeddingStoreConfig defaultParameters;
    private static String iamEmail;
    private static String projectId;
    private static String region;
    private static String cluster;
    private static String instance;
    private static String database;
    private static String user;
    private static String password;

    private static AlloyDBEngine engine;
    private static Connection defaultConnection;

    @BeforeAll
    public static void beforeAll() throws SQLException {
        if (engine == null) {
            engine = new AlloyDBEngine.Builder()
                    .host(pgVector.getHost())
                    .port(pgVector.getFirstMappedPort())
                    .user("test")
                    .password("test")
                    .database("test")
                    .build();
        }
        defaultConnection = engine.getConnection();

        defaultConnection
                .createStatement()
                .executeUpdate(String.format("CREATE SCHEMA IF NOT EXISTS \"%s\"", CUSTOM_SCHEMA));

        defaultParameters = new EmbeddingStoreConfig.Builder(TABLE_NAME, VECTOR_SIZE).build();
    }

    @AfterEach
    public void afterEach() throws SQLException {
        defaultConnection.createStatement().executeUpdate(String.format("DROP TABLE IF EXISTS \"%s\"", TABLE_NAME));
        defaultConnection
                .createStatement()
                .executeUpdate(String.format("DROP TABLE IF EXISTS \"%s\"", CUSTOM_TABLE_NAME));
        defaultConnection
                .createStatement()
                .executeUpdate(String.format("DROP TABLE IF EXISTS \"%s\".\"%s\"", CUSTOM_SCHEMA, TABLE_NAME));
        defaultConnection
                .createStatement()
                .executeUpdate(String.format("DROP TABLE IF EXISTS \"%s\".\"%s\"", CUSTOM_SCHEMA, CUSTOM_TABLE_NAME));
    }

    @AfterAll
    public static void afterAll() throws SQLException {
        engine.close();
    }

    @Test
    void initialize_vector_table_with_default_schema() throws SQLException {
        // default options
        engine.initVectorStoreTable(defaultParameters);

        Set<String> expectedNames = new HashSet<>();

        expectedNames.add("langchain_id");
        expectedNames.add("content");
        expectedNames.add("embedding");
        expectedNames.add("langchain_metadata");

        AlloyDBTestUtils.verifyColumns(defaultConnection, "public", TABLE_NAME, expectedNames);
    }

    @Test
    void initialize_vector_table_overwrite_true() throws SQLException {
        // default options
        engine.initVectorStoreTable(defaultParameters);
        // custom
        EmbeddingStoreConfig overwritten = new EmbeddingStoreConfig.Builder(TABLE_NAME, VECTOR_SIZE)
                .contentColumn("overwritten")
                .overwriteExisting(true)
                .build();
        engine.initVectorStoreTable(overwritten);

        Set<String> expectedColumns = new HashSet<>();
        expectedColumns.add("langchain_id");
        expectedColumns.add("overwritten");
        expectedColumns.add("embedding");
        expectedColumns.add("langchain_metadata");

        AlloyDBTestUtils.verifyColumns(defaultConnection, "public", TABLE_NAME, expectedColumns);
    }

    @Test
    void initialize_vector_table_with_custom_options() throws SQLException {
        List<MetadataColumn> metadataColumns = new ArrayList<>();
        metadataColumns.add(new MetadataColumn("page", "TEXT", true));
        metadataColumns.add(new MetadataColumn("source", "TEXT", false));

        EmbeddingStoreConfig customParams = new EmbeddingStoreConfig.Builder(CUSTOM_TABLE_NAME, 1000)
                .schemaName(CUSTOM_SCHEMA)
                .contentColumn("custom_content_column")
                .embeddingColumn("custom_embedding_column")
                .idColumn("custom_embedding_id_column")
                .metadataColumns(metadataColumns)
                .metadataJsonColumn("custom_metadata_json_column")
                .overwriteExisting(false)
                .storeMetadata(true)
                .build();
        engine.initVectorStoreTable(customParams);
        Set<String> expectedColumns = new HashSet<>();
        expectedColumns.add("custom_embedding_id_column");
        expectedColumns.add("custom_content_column");
        expectedColumns.add("custom_embedding_column");
        expectedColumns.add("page");
        expectedColumns.add("source");
        expectedColumns.add("custom_metadata_json_column");

        AlloyDBTestUtils.verifyColumns(defaultConnection, CUSTOM_SCHEMA, CUSTOM_TABLE_NAME, expectedColumns);
    }

    @Test
    void create_fails_when_table_present_and_overwrite_false() {
        EmbeddingStoreConfig initParameters = new EmbeddingStoreConfig.Builder(CUSTOM_TABLE_NAME, VECTOR_SIZE)
                .storeMetadata(false)
                .build();

        engine.initVectorStoreTable(initParameters);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.initVectorStoreTable(initParameters);
        });

        assertThat(exception.getMessage())
                .isEqualTo(
                        String.format("Failed to initialize vector store table: \"public\".\"%s\"", CUSTOM_TABLE_NAME));
        // table name is truncated in PSQL exception
        assertThat(exception.getCause().getMessage())
                .isEqualTo(String.format(
                        "ERROR: relation \"%s\" already exists",
                        CUSTOM_TABLE_NAME.substring(0, CUSTOM_TABLE_NAME.length() - 2)));
    }

    @Disabled("Test against Google Cloud only")
    @Test
    void create_engine_with_iam_auth() throws SQLException {
        AlloyDBEngine iamEngine = new AlloyDBEngine.Builder()
                .projectId(projectId)
                .region(region)
                .cluster(cluster)
                .instance(instance)
                .database(database)
                .iamAccountEmail(iamEmail)
                .build();
        try (Connection connection = iamEngine.getConnection(); ) {
            ResultSet rs = connection.createStatement().executeQuery("SELECT 1");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Disabled("Test against Google Cloud only")
    @Test
    void create_engine_with_get_iam_email() throws SQLException {
        AlloyDBEngine iamEngine = new AlloyDBEngine.Builder()
                .projectId(projectId)
                .region(region)
                .cluster(cluster)
                .instance(instance)
                .database(database)
                .build();
        try (Connection connection = iamEngine.getConnection(); ) {
            ResultSet rs = connection.createStatement().executeQuery("SELECT 1");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }
}
