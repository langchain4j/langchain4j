package dev.langchain4j.data.document.loader.alloydb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.engine.AlloyDBEngine;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * This class represents unit tests for {@link AlloyDBLoader}.
 */
public class AlloyDBLoaderTest {

    @Mock
    private AlloyDBEngine mockAlloyDBEngine;

    @Mock
    private Connection mockConnection;

    @Mock
    private PreparedStatement mockStatement;

    @Mock
    private ResultSet mockResultSet;

    @Mock
    private ResultSetMetaData mockResultSetMetaData;

    private AlloyDBLoader.Builder builder;

    @BeforeEach
    public void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);
        when(mockAlloyDBEngine.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.getMetaData()).thenReturn(mockResultSetMetaData);
        builder = new AlloyDBLoader.Builder(mockAlloyDBEngine);
        builder.tableName("testTable");
    }

    @Test
    public void testBuildWithQuery() throws SQLException {
        when(mockResultSetMetaData.getColumnCount()).thenReturn(2);
        when(mockResultSetMetaData.getColumnName(1)).thenReturn("col1");
        when(mockResultSetMetaData.getColumnName(2)).thenReturn("col2");

        AlloyDBLoader loader = builder.query("SELECT * FROM test_table").build();

        assertNotNull(loader);
    }

    @Test
    public void testBuildWithTableName() throws SQLException {
        when(mockResultSetMetaData.getColumnCount()).thenReturn(2);
        when(mockResultSetMetaData.getColumnName(1)).thenReturn("col1");
        when(mockResultSetMetaData.getColumnName(2)).thenReturn("col2");

        AlloyDBLoader loader = builder.tableName("test_table").build();

        assertNotNull(loader);
    }

    @Test
    public void testBuildWithInvalidColumns() throws SQLException {
        when(mockResultSetMetaData.getColumnCount()).thenReturn(1);
        when(mockResultSetMetaData.getColumnName(1)).thenReturn("col1");

        builder.contentColumns(Arrays.asList("invalid_col"));

        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void testBuildWithInvalidMetadataJsonColumn() throws SQLException {
        when(mockResultSetMetaData.getColumnCount()).thenReturn(1);
        when(mockResultSetMetaData.getColumnName(1)).thenReturn("col1");

        builder.metadataJsonColumn("invalid_json_col");

        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void testLoadDocuments() throws SQLException, ExecutionException, InterruptedException {
        when(mockResultSetMetaData.getColumnCount()).thenReturn(3);
        when(mockResultSetMetaData.getColumnName(1)).thenReturn("content");
        when(mockResultSetMetaData.getColumnName(2)).thenReturn("metadata");
        when(mockResultSetMetaData.getColumnName(3)).thenReturn("langchain_metadata");

        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getString("content")).thenReturn("test content");
        when(mockResultSet.getObject("metadata")).thenReturn("test metadata");
        when(mockResultSet.getObject("langchain_metadata")).thenReturn("{\"key\":\"value\"}");

        AlloyDBLoader loader = builder.contentColumns(Arrays.asList("content"))
                .metadataColumns(Arrays.asList("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .build();

        List<Document> documents = loader.load();

        assertEquals(1, documents.size());
        assertEquals("test content", documents.get(0).text());
        assertEquals("value", ((Metadata) documents.get(0).metadata()).toMap().get("key"));
        assertEquals(
                "test metadata",
                ((Metadata) documents.get(0).metadata()).toMap().get("metadata"));
    }
}
