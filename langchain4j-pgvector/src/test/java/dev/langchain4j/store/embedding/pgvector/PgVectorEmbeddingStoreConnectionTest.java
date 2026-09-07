package dev.langchain4j.store.embedding.pgvector;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;

class PgVectorEmbeddingStoreConnectionTest {

    private static final String CREATE_EXTENSION = "CREATE EXTENSION IF NOT EXISTS vector";

    private static PgVectorEmbeddingStore storeWith(DataSource dataSource) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("embeddings")
                .dropTableFirst(false)
                .createTable(false)
                .useIndex(false)
                .build();
    }

    @Test
    void should_close_connection_when_creating_the_vector_extension_fails() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeUpdate(CREATE_EXTENSION))
                .thenThrow(new SQLException("permission denied to create extension \"vector\""));

        PgVectorEmbeddingStore store = storeWith(dataSource);

        assertThatThrownBy(store::getConnection).isInstanceOf(SQLException.class);

        verify(connection).close();
    }

    @Test
    void should_close_connection_when_creating_the_statement_fails() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenThrow(new SQLException("connection is closed"));

        PgVectorEmbeddingStore store = storeWith(dataSource);

        assertThatThrownBy(store::getConnection).isInstanceOf(SQLException.class);

        verify(connection).close();
    }

    @Test
    void should_close_connection_when_registering_the_vector_type_fails() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        // PGvector.addVectorType() unwraps the connection to register the vector type.
        when(connection.unwrap(PGConnection.class)).thenThrow(new SQLException("cannot unwrap to PGConnection"));

        PgVectorEmbeddingStore store = storeWith(dataSource);

        assertThatThrownBy(store::getConnection).isInstanceOf(SQLException.class);

        verify(connection).close();
    }
}
