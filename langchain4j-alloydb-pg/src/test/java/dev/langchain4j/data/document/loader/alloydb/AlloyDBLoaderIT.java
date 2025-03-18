package dev.langchain4j.data.document.loader.alloydb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.engine.AlloyDBEngine;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class AlloyDBLoaderIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    private static AlloyDBEngine engine;
    private static Connection connection;

    @BeforeAll
    public static void beforeAll() throws SQLException {
        engine = new AlloyDBEngine.Builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .build();
        connection = engine.getConnection();
    }

    @BeforeEach
    public void setUp() throws SQLException {
        createTableAndInsertData();
    }

    private void createTableAndInsertData() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE test_table (id SERIAL PRIMARY KEY, content TEXT, metadata TEXT, langchain_metadata JSONB)");
            statement.execute(
                    "INSERT INTO test_table (content, metadata, langchain_metadata) VALUES ('test content 1', 'test metadata 1', '{\"key\": \"value1\"}')");
            statement.execute(
                    "INSERT INTO test_table (content, metadata, langchain_metadata) VALUES ('test content 2', 'test metadata 2', '{\"key\": \"value2\"}')");
        }
    }

    @AfterEach
    public void afterEach() throws SQLException {
        connection.createStatement().executeUpdate("DROP TABLE IF EXISTS test_table");
    }

    @AfterAll
    public static void afterAll() throws SQLException {
        engine.close();
    }

    @Test
    public void testLoadDocumentsFromDatabase() throws SQLException {
        AlloyDBLoader loader = new AlloyDBLoader.Builder(engine)
                .tableName("test_table")
                .contentColumns(Arrays.asList("content"))
                .metadataColumns(Arrays.asList("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .build();

        List<Document> documents = loader.load();

        assertNotNull(documents);
        assertEquals(2, documents.size());

        assertEquals("test content 1", documents.get(0).text());
        assertEquals("value1", documents.get(0).metadata().asMap().get("key"));
        assertEquals("test metadata 1", documents.get(0).metadata().asMap().get("metadata"));

        assertEquals("test content 2", documents.get(1).text());
        assertEquals("value2", documents.get(1).metadata().asMap().get("key"));
        assertEquals("test metadata 2", documents.get(1).metadata().asMap().get("metadata"));
    }

    @Test
    public void testLoadDocumentsWithCustomQuery() throws SQLException {
        AlloyDBLoader loader = new AlloyDBLoader.Builder(engine)
                .query("SELECT content, metadata, langchain_metadata FROM test_table WHERE id = 1")
                .contentColumns(Arrays.asList("content"))
                .metadataColumns(Arrays.asList("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .build();

        List<Document> documents = loader.load();

        assertNotNull(documents);
        assertEquals(1, documents.size());

        assertEquals("test content 1", documents.get(0).text());
        assertEquals("value1", documents.get(0).metadata().asMap().get("key"));
        assertEquals("test metadata 1", documents.get(0).metadata().asMap().get("metadata"));
    }

    @Test
    public void testLoadDocumentsWithTextFormatter() throws SQLException {
        AlloyDBLoader loader = new AlloyDBLoader.Builder(engine)
                .tableName("test_table")
                .contentColumns(Arrays.asList("content"))
                .metadataColumns(Arrays.asList("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .format("text")
                .build();

        List<Document> documents = loader.load();

        assertEquals("test content 1", documents.get(0).text());
        assertEquals("test content 2", documents.get(1).text());
    }

    @Test
    public void testLoadDocumentsWithCsvFormatter() throws SQLException {
        AlloyDBLoader loader = new AlloyDBLoader.Builder(engine)
                .tableName("test_table")
                .contentColumns(Arrays.asList("content"))
                .metadataColumns(Arrays.asList("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .format("csv")
                .build();

        List<Document> documents = loader.load();

        assertEquals("test content 1,", documents.get(0).text());
        assertEquals("test content 2,", documents.get(1).text());
    }

    @Test
    public void testLoadDocumentsWithYamlFormatter() throws SQLException {
        AlloyDBLoader loader = new AlloyDBLoader.Builder(engine)
                .tableName("test_table")
                .contentColumns(Arrays.asList("content"))
                .metadataColumns(Arrays.asList("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .format("YAML")
                .build();

        List<Document> documents = loader.load();

        assertEquals("content: test content 1", documents.get(0).text());
        assertEquals("content: test content 2", documents.get(1).text());
    }

    @Test
    public void testLoadDocumentsWithJsonFormatter() throws SQLException {
        AlloyDBLoader loader = new AlloyDBLoader.Builder(engine)
                .tableName("test_table")
                .contentColumns(Arrays.asList("content"))
                .metadataColumns(Arrays.asList("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .format("JSON")
                .build();

        List<Document> documents = loader.load();

        assertEquals("{\"content\":\"test content 1\"}", documents.get(0).text());
        assertEquals("{\"content\":\"test content 2\"}", documents.get(1).text());
    }
}
