package dev.langchain4j.store.embedding.oracle.vecdb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
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
    public String createVectorTable(
            Connection connection,
            VecDbEmbeddingTable table,
            String annotationsJson,
            String tableParametersJson,
            String indexParametersJson)
            throws SQLException {
        return call(
                connection,
                "BEGIN ? := DBMS_VECTOR_DATABASE.CREATE_VECTOR_TABLE("
                        + "name => ?, comment => ?, annotations => ?, table_params => ?, "
                        + "embed_params => NULL, index_params => ?); END;",
                statement -> {
                    statement.setString(2, table.name());
                    statement.setString(3, table.comment());
                    setJson(statement, 4, annotationsJson);
                    setJson(statement, 5, tableParametersJson);
                    setJson(statement, 6, indexParametersJson);
                });
    }

    @Override
    public String describeVectorTable(Connection connection, String tableName) throws SQLException {
        return call(
                connection,
                "BEGIN ? := DBMS_VECTOR_DATABASE.DESCRIBE_VECTOR_TABLE(name => ?); END;",
                statement -> statement.setString(2, tableName));
    }

    @Override
    public String dropVectorTable(Connection connection, String tableName) throws SQLException {
        return call(
                connection,
                "BEGIN ? := DBMS_VECTOR_DATABASE.DROP_VECTOR_TABLE(name => ?); END;",
                statement -> statement.setString(2, tableName));
    }

    @Override
    public IndexStatus indexStatus(Connection connection, String tableName) throws SQLException {
        JsonNode description = readTree(describeVectorTable(connection, tableName));
        boolean vectorIndexExists = false;
        boolean metadataIndexExists = false;
        JsonNode indexes = field(description, "indexes");
        if (indexes != null && indexes.isArray()) {
            for (JsonNode index : indexes) {
                if (isVectorIndex(index)) {
                    vectorIndexExists = true;
                }
                if (isMetadataIndex(index)) {
                    metadataIndexExists = true;
                }
            }
        }

        JsonNode indexParameters = field(description, "index_params");
        JsonNode vectorIndexParameters = field(indexParameters, "vector_index_params");
        if (!vectorIndexExists) {
            vectorIndexExists = isAutoIndexEnabled(vectorIndexParameters);
        }

        JsonNode metadataIndexParameters = field(indexParameters, "metadata_index_params");
        if (!metadataIndexExists) {
            metadataIndexExists = isAutoIndexEnabled(metadataIndexParameters)
                    || hasConfiguredPaths(metadataIndexParameters, "include_paths");
        }

        return new IndexStatus(vectorIndexExists, metadataIndexExists);
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

    private static void setJson(CallableStatement statement, int parameterIndex, String json) throws SQLException {
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

    private static boolean isVectorIndex(JsonNode index) {
        if (index.isTextual()) {
            return index.asText().toUpperCase(Locale.ROOT).startsWith("VECIDX");
        }
        if (!index.isObject()) {
            return false;
        }

        JsonNode type = field(index, "index_type");
        if (type == null) {
            type = field(index, "type");
        }
        if (type != null && type.asText().toUpperCase(Locale.ROOT).contains("VECTOR")) {
            return true;
        }

        JsonNode name = field(index, "index_name");
        if (name == null) {
            name = field(index, "name");
        }
        return name != null && name.asText().toUpperCase(Locale.ROOT).startsWith("VECIDX");
    }

    private static boolean isMetadataIndex(JsonNode index) {
        if (index.isTextual()) {
            String value = index.asText().toUpperCase(Locale.ROOT);
            return value.startsWith("MVI") || value.contains("METADATA");
        }
        if (!index.isObject()) {
            return false;
        }

        JsonNode type = field(index, "index_type");
        if (type == null) {
            type = field(index, "type");
        }
        if (type != null && type.asText().toUpperCase(Locale.ROOT).contains("METADATA")) {
            return true;
        }

        JsonNode name = field(index, "index_name");
        if (name == null) {
            name = field(index, "name");
        }
        if (name == null) {
            return false;
        }
        String value = name.asText().toUpperCase(Locale.ROOT);
        return value.startsWith("MVI") || value.contains("METADATA");
    }

    private static boolean isAutoIndexEnabled(JsonNode indexParameters) {
        JsonNode autoIndex = field(indexParameters, "auto_index");
        return autoIndex != null && autoIndex.asBoolean(false);
    }

    private static boolean hasConfiguredPaths(JsonNode indexParameters, String fieldName) {
        JsonNode paths = field(indexParameters, fieldName);
        return paths != null && paths.isArray() && !paths.isEmpty();
    }

    private static String unquote(String identifier) {
        if (identifier.length() >= 2 && identifier.startsWith("\"") && identifier.endsWith("\"")) {
            return identifier.substring(1, identifier.length() - 1);
        }
        return identifier;
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(CallableStatement statement) throws SQLException;
    }
}
