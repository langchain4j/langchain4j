package dev.langchain4j.store.embedding.oracle.vecdb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.store.embedding.oracle.SQLFilter;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbApiVersion;
import dev.langchain4j.store.embedding.oracle.vecdb.mapper.VecDbEmbeddingTableJsonMapper;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import oracle.jdbc.OracleType;
import oracle.jdbc.OracleTypes;
import oracle.sql.json.OracleJsonFactory;

/**
 * JDBC implementation of {@link VecDbQueryExecutor} backed by {@code DBMS_VECTOR_DATABASE}.
 */
final class VecDbJdbcQueryExecutor implements VecDbQueryExecutor {

    private static final OracleJsonFactory JSON_FACTORY = new OracleJsonFactory();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public boolean vectorTableExists(Connection connection, String tableName) throws SQLException {
        String response = call(connection, "BEGIN ? := DBMS_VECTOR_DATABASE.LIST_VECTOR_TABLES(); END;");
        JsonNode vectorTables = field(readTree(response), "vector_tables");
        if (vectorTables == null || !vectorTables.isArray()) {
            return false;
        }

        String expectedName = unquote(tableName);
        for (JsonNode vectorTable : vectorTables) {
            JsonNode actualName = field(vectorTable, "table_name");
            if (actualName != null && expectedName.equalsIgnoreCase(actualName.asText())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public VecDbTableLayout inspectTableLayout(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String metadataTableName = isQuoted(tableName) ? unquote(tableName) : tableName.toUpperCase(Locale.ROOT);
        Set<String> columns = new LinkedHashSet<>();
        try (ResultSet resultSet = metadata.getColumns(null, connection.getSchema(), metadataTableName, null)) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("COLUMN_NAME"));
            }
        }
        return new VecDbTableLayout(columns);
    }

    @Override
    public void applyTableMigration(Connection connection, String tableName, VecDbTableMigration.Action action)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(tableMigrationSql(tableName, action));
        }
    }

    static String tableMigrationSql(String tableName, VecDbTableMigration.Action action) {
        return switch (action) {
            case RENAME_METADATA_TO_CONTENT_METADATA ->
                "ALTER TABLE " + tableName + " RENAME COLUMN METADATA TO CONTENT_METADATA";
            case ADD_SPARSE_VECTOR -> "ALTER TABLE " + tableName + " ADD SPARSE_VECTOR VECTOR(*, *, SPARSE)";
            case ADD_CONTENT -> "ALTER TABLE " + tableName + " ADD CONTENT BLOB";
            case ADD_CONTENT_TYPE -> "ALTER TABLE " + tableName + " ADD CONTENT_TYPE VARCHAR2(256)";
        };
    }

    @Override
    public String createVectorTable(
            Connection connection,
            VecDbApiVersion apiVersion,
            VecDbEmbeddingTable table,
            String annotationsJson,
            String tableParametersJson,
            String indexParametersJson)
            throws SQLException {
        VecDbApiDialect dialect = VecDbApiDialect.forVersion(apiVersion);
        VecDbApiDialect.CreateVectorTableRequest request = new VecDbApiDialect.CreateVectorTableRequest(
                table, annotationsJson, tableParametersJson, indexParametersJson);
        return call(
                connection,
                dialect.createVectorTableCall(),
                statement -> dialect.bindCreateVectorTable(statement, request));
    }

    @Override
    public String describeVectorTable(Connection connection, String tableName, VecDbApiVersion apiVersion)
            throws SQLException {
        VecDbApiDialect dialect = VecDbApiDialect.forVersion(apiVersion);
        return call(connection, dialect.describeVectorTableCall(), statement -> statement.setString(2, tableName));
    }

    @Override
    public String dropVectorTable(Connection connection, String tableName, VecDbApiVersion apiVersion)
            throws SQLException {
        VecDbApiDialect dialect = VecDbApiDialect.forVersion(apiVersion);
        return call(connection, dialect.dropVectorTableCall(), statement -> statement.setString(2, tableName));
    }

    @Override
    public IndexStatus indexStatus(Connection connection, String tableName, VecDbApiVersion apiVersion)
            throws SQLException {
        String description = describeVectorTable(connection, tableName, apiVersion);
        VecDbEmbeddingTableJsonMapper.IndexStatus status =
                VecDbEmbeddingTableJsonMapper.indexStatusFromJson(description, apiVersion);
        return new IndexStatus(status.vectorIndexExists(), status.metadataIndexExists());
    }

    @Override
    public String createIndex(Connection connection, String tableName, String indexParametersJson) throws SQLException {
        return call(
                connection,
                "BEGIN ? := DBMS_VECTOR_DATABASE.CREATE_INDEX(table_name => ?, index_params => ?); END;",
                statement -> {
                    statement.setString(2, tableName);
                    setJson(statement, 3, indexParametersJson);
                });
    }

    @Override
    public String dropIndex(Connection connection, String tableName, String indexParametersJson) throws SQLException {
        return call(
                connection,
                "BEGIN ? := DBMS_VECTOR_DATABASE.DROP_INDEX(table_name => ?, index_params => ?); END;",
                statement -> {
                    statement.setString(2, tableName);
                    setJson(statement, 3, indexParametersJson);
                });
    }

    @Override
    public String rebuildIndex(Connection connection, String tableName, String indexParametersJson)
            throws SQLException {
        return call(
                connection,
                "BEGIN ? := DBMS_VECTOR_DATABASE.REBUILD_INDEX(table_name => ?, index_params => ?); END;",
                statement -> {
                    statement.setString(2, tableName);
                    setJson(statement, 3, indexParametersJson);
                });
    }

    @Override
    public String upsertVectors(Connection connection, String tableName, String vectorsJson) throws SQLException {
        return call(
                connection,
                "BEGIN ? := DBMS_VECTOR_DATABASE.UPSERT_VECTORS(table_name => ?, vectors => ?); END;",
                statement -> {
                    statement.setString(2, tableName);
                    setJson(statement, 3, vectorsJson);
                });
    }

    @Override
    public String listVectors(Connection connection, String tableName, String idsJson, int limit, int offset)
            throws SQLException {
        return call(
                connection,
                "BEGIN ? := DBMS_VECTOR_DATABASE.LIST_VECTORS("
                        + "table_name => ?, ids => ?, limit => ?, offset => ?); END;",
                statement -> {
                    statement.setString(2, tableName);
                    setJson(statement, 3, idsJson);
                    statement.setInt(4, limit);
                    statement.setInt(5, offset);
                });
    }

    @Override
    public String search(
            Connection connection,
            String tableName,
            String queryJson,
            String filtersJson,
            int maxResults,
            boolean includeVectors,
            String advancedOptionsJson)
            throws SQLException {
        String includeVectorsLiteral = includeVectors ? "TRUE" : "FALSE";
        return call(
                connection,
                "BEGIN ? := DBMS_VECTOR_DATABASE.SEARCH("
                        + "table_name => ?, query_by => ?, filters => ?, top_k => ?, include_vectors => "
                        + includeVectorsLiteral
                        + ", advanced_options => ?); END;",
                statement -> {
                    statement.setString(2, tableName);
                    setJson(statement, 3, queryJson);
                    setJson(statement, 4, filtersJson);
                    statement.setInt(5, maxResults);
                    setJson(statement, 6, advancedOptionsJson);
                });
    }

    @Override
    public String deleteVectors(Connection connection, String tableName, String idsJson) throws SQLException {
        return call(
                connection,
                "BEGIN ? := DBMS_VECTOR_DATABASE.DELETE_VECTORS(table_name => ?, ids => ?); END;",
                statement -> {
                    statement.setString(2, tableName);
                    setJson(statement, 3, idsJson);
                });
    }

    @Override
    public int deleteVectorsByFilter(Connection connection, String tableName, SQLFilter filter) throws SQLException {
        String sql = "DELETE FROM " + tableName + filter.asWhereClause();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            filter.setParameters(statement, 1);
            return statement.executeUpdate();
        }
    }

    @Override
    public int deleteAllVectors(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate("DELETE FROM " + tableName);
        }
    }

    private static String call(Connection connection, String sql, StatementBinder... binders) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(sql)) {
            statement.registerOutParameter(1, OracleTypes.CLOB);
            for (StatementBinder binder : binders) {
                binder.bind(statement);
            }
            statement.execute();
            return readClob(statement.getClob(1));
        }
    }

    static void setJson(CallableStatement statement, int parameterIndex, String json) throws SQLException {
        if (json == null) {
            statement.setObject(parameterIndex, null, OracleType.JSON);
        } else {
            statement.setObject(
                    parameterIndex, JSON_FACTORY.createJsonTextValue(new StringReader(json)), OracleType.JSON);
        }
    }

    private static String readClob(Clob clob) throws SQLException {
        if (clob == null) {
            return null;
        }

        try (Reader reader = clob.getCharacterStream()) {
            StringBuilder value = new StringBuilder();
            char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                value.append(buffer, 0, count);
            }
            return value.toString();
        } catch (IOException exception) {
            throw new SQLException("Failed to read DBMS_VECTOR_DATABASE response", exception);
        } finally {
            clob.free();
        }
    }

    private static JsonNode readTree(String json) throws SQLException {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new SQLException("DBMS_VECTOR_DATABASE returned invalid JSON", exception);
        }
    }

    private static JsonNode field(JsonNode object, String fieldName) {
        if (object == null || !object.isObject()) {
            return null;
        }

        for (Map.Entry<String, JsonNode> field : object.properties()) {
            if (fieldName.equalsIgnoreCase(field.getKey())) {
                return field.getValue();
            }
        }
        return null;
    }

    private static String unquote(String identifier) {
        if (isQuoted(identifier)) {
            return identifier.substring(1, identifier.length() - 1);
        }
        return identifier;
    }

    private static boolean isQuoted(String identifier) {
        return identifier.length() >= 2 && identifier.startsWith("\"") && identifier.endsWith("\"");
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(CallableStatement statement) throws SQLException;
    }
}
