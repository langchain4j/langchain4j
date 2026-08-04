package dev.langchain4j.store.embedding.oracle.vecdb;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.oracle.CreateOption;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbDistanceMetric;
import dev.langchain4j.store.embedding.oracle.vecdb.mapper.VecDbVectorJsonMapper;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;

/**
 * Creates stores and cleans database objects shared by the VecDB contract tests.
 */
final class VecDbTestOperations {

    private static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private static final VecDbQueryExecutor QUERY_EXECUTOR = new VecDbJdbcQueryExecutor();

    private VecDbTestOperations() {}

    static DataSource dataSource() {
        return VecDbTestContainer.dataSource();
    }

    static EmbeddingModel embeddingModel() {
        return EMBEDDING_MODEL;
    }

    static OracleVecDbEmbeddingStore createStore(String tableName) {
        return OracleVecDbEmbeddingStore.builder()
                .dataSource(dataSource())
                .embeddingTable(tableName, CreateOption.CREATE_OR_REPLACE)
                .distanceMetric(VecDbDistanceMetric.COSINE)
                .build();
    }

    static boolean vectorTableExists(String tableName) throws SQLException {
        try (Connection connection = dataSource().getConnection()) {
            return QUERY_EXECUTOR.vectorTableExists(connection, tableName);
        }
    }

    static List<String> listVectorIds(String tableName) throws SQLException {
        try (Connection connection = dataSource().getConnection()) {
            String responseJson = QUERY_EXECUTOR.listVectors(connection, tableName, null, 100, 0);
            return VecDbVectorJsonMapper.idsFromListResponse(responseJson);
        }
    }

    static String describeVectorTable(String tableName) throws SQLException {
        try (Connection connection = dataSource().getConnection()) {
            return QUERY_EXECUTOR.describeVectorTable(
                    connection, tableName, VecDbSupport.resolveApiVersion(connection));
        }
    }

    static VecDbQueryExecutor.IndexStatus indexStatus(String tableName) throws SQLException {
        try (Connection connection = dataSource().getConnection()) {
            return QUERY_EXECUTOR.indexStatus(connection, tableName, VecDbSupport.resolveApiVersion(connection));
        }
    }

    static void dropVectorTable(String tableName) throws SQLException {
        try (Connection connection = dataSource().getConnection()) {
            if (QUERY_EXECUTOR.vectorTableExists(connection, tableName)) {
                QUERY_EXECUTOR.dropVectorTable(connection, tableName, VecDbSupport.resolveApiVersion(connection));
            }
        }
    }
}
