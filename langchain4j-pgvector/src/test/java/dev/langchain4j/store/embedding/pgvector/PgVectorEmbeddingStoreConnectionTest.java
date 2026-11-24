package dev.langchain4j.store.embedding.pgvector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void should_return_an_open_connection_when_initialization_succeeds() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        // PGvector.addVectorType() unwraps the connection to register the vector type.
        when(connection.unwrap(PGConnection.class)).thenReturn(mock(PGConnection.class));

        PgVectorEmbeddingStore store = storeWith(dataSource);

        assertThat(store.getConnection()).isSameAs(connection);

        verify(statement).executeUpdate(CREATE_EXTENSION);
        verify(connection, never()).close();
    }

    @Test
    void should_close_connection_when_creating_the_vector_extension_fails() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        SQLException failure = new SQLException("permission denied to create extension \"vector\"");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeUpdate(CREATE_EXTENSION)).thenThrow(failure);

        PgVectorEmbeddingStore store = storeWith(dataSource);

        assertThatThrownBy(store::getConnection).isSameAs(failure);

        verify(connection).close();
    }

    @Test
    void should_close_connection_when_creating_the_statement_fails() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        SQLException failure = new SQLException("connection is closed");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenThrow(failure);

        PgVectorEmbeddingStore store = storeWith(dataSource);

        assertThatThrownBy(store::getConnection).isSameAs(failure);

        verify(connection).close();
    }

    @Test
    void should_close_connection_when_registering_the_vector_type_fails() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        SQLException failure = new SQLException("cannot unwrap to PGConnection");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        // PGvector.addVectorType() unwraps the connection to register the vector type.
        when(connection.unwrap(PGConnection.class)).thenThrow(failure);

        PgVectorEmbeddingStore store = storeWith(dataSource);

        assertThatThrownBy(store::getConnection).isSameAs(failure);

        verify(connection).close();
    }

    @Test
    void should_suppress_the_close_failure_on_the_original_exception() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        SQLException failure = new SQLException("permission denied to create extension \"vector\"");
        SQLException closeFailure = new SQLException("connection has already been returned to the pool");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeUpdate(CREATE_EXTENSION)).thenThrow(failure);
        doThrow(closeFailure).when(connection).close();

        PgVectorEmbeddingStore store = storeWith(dataSource);

        assertThatThrownBy(store::getConnection).isSameAs(failure).hasSuppressedException(closeFailure);
    }
}
