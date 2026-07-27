package dev.langchain4j.store.embedding.oracle.vecdb;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.oracle.CreateOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;

/** Shared store-level operations used by VecDB integration tests. */
final class VecDbTestOperations {

    private static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private static final VecDbQueryExecutor QUERY_EXECUTOR = new VecDbJdbcQueryExecutor();

    private VecDbTestOperations() {}

    static DataSource dataSource() {
        return VecDbTestEnvironment.dataSource();
    }

    static EmbeddingModel embeddingModel() {
        return EMBEDDING_MODEL;
    }

    static OracleVecDbEmbeddingStore newEmbeddingStore(String tableName) {
        return OracleVecDbEmbeddingStore.builder()
                .dataSource(dataSource())
                .embeddingTable(tableName, CreateOption.CREATE_OR_REPLACE)
                .build();
    }

    static void dropVectorTable(String tableName) throws SQLException {
        try (Connection connection = dataSource().getConnection()) {
            if (QUERY_EXECUTOR.vectorTableExists(connection, tableName)) {
                QUERY_EXECUTOR.dropVectorTable(connection, tableName);
            }
        }
    }

    static int numberOfStoredVectorsWithId(String tableName, String id) throws SQLException {
        try (Connection connection = dataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM " + tableName + " WHERE ID = ? AND DENSE_VECTOR IS NOT NULL")) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }
}
