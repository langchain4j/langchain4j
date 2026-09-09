package dev.langchain4j.store.embedding.pgvector;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PgVectorEmbeddingStoreNoConnectionRequiredTest {

    @Test
    void noConnectionRequired() {
        DataSource dataSource = Mockito.mock(DataSource.class);

        PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("embeddings")
                .dropTableFirst(false)
                .createTable(false)
                .useIndex(false)
                .build();

        Mockito.verifyNoInteractions(dataSource);
    }

    @Test
    void closesConnectionWhenVectorExtensionInitializationFails() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        SQLException failure = new SQLException("permission denied");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        doThrow(failure).when(statement).executeUpdate("CREATE EXTENSION IF NOT EXISTS vector");

        PgVectorEmbeddingStore store = PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("embeddings")
                .dropTableFirst(false)
                .createTable(false)
                .useIndex(false)
                .build();

        assertThatThrownBy(store::getConnection).isSameAs(failure);
        verify(connection).close();
    }
}
