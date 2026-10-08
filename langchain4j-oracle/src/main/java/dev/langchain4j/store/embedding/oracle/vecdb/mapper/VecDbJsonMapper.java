package dev.langchain4j.store.embedding.oracle.vecdb.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import oracle.sql.json.OracleJsonArray;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonGenerator;
import oracle.sql.json.OracleJsonObject;
import oracle.sql.json.OracleJsonValue;

/** Converts request JSON to typed Oracle values and measures their OSON encoding. */
public final class VecDbJsonMapper {

    /** Keep each encoded request strictly below Oracle's 32 MiB JSON-instance limit. */
    static final int MAX_OSON_BYTES = 32 * 1024 * 1024 - 1;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final OracleJsonFactory JSON_FACTORY = new OracleJsonFactory();

    private VecDbJsonMapper() {}

    /** Converts JSON text while retaining floating-point values as Oracle binary doubles. */
    public static OracleJsonValue toOracleJsonValue(String json) throws SQLException {
        JsonNode node;
        try {
            node = OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid JSON parameter", exception);
        }
        if (node == null || node.isMissingNode()) {
            throw new SQLException("Invalid JSON parameter: a JSON value is required");
        }
        return toOracleJsonValue(node);
    }

    private static OracleJsonValue toOracleJsonValue(JsonNode node) throws SQLException {
        if (node.isObject()) {
            OracleJsonObject object = JSON_FACTORY.createObject();
            for (Map.Entry<String, JsonNode> property : node.properties()) {
                object.put(property.getKey(), toOracleJsonValue(property.getValue()));
            }
            return object;
        }
        if (node.isArray()) {
            OracleJsonArray array = JSON_FACTORY.createArray();
            for (JsonNode element : node) {
                array.add(toOracleJsonValue(element));
            }
            return array;
        }
        if (node.isTextual()) {
            return JSON_FACTORY.createString(node.textValue());
        }
        if (node.isIntegralNumber()) {
            if (node.canConvertToInt()) {
                return JSON_FACTORY.createDecimal(node.intValue());
            }
            if (node.canConvertToLong()) {
                return JSON_FACTORY.createDecimal(node.longValue());
            }
            return JSON_FACTORY.createDecimal(node.decimalValue());
        }
        if (node.isFloatingPointNumber()) {
            // Match the former float -> JSON text -> double conversion without reparsing the whole request.
            double value = node.isFloat() ? Double.parseDouble(node.asText()) : node.doubleValue();
            if (!Double.isFinite(value)) {
                throw new SQLException("Invalid JSON parameter: floating-point values must be finite");
            }
            return JSON_FACTORY.createDouble(value);
        }
        if (node.isBoolean()) {
            return JSON_FACTORY.createBoolean(node.booleanValue());
        }
        if (node.isNull()) {
            return JSON_FACTORY.createNull();
        }
        throw new SQLException("Invalid JSON parameter: unsupported JSON node type " + node.getNodeType());
    }

    /** Encodes a complete array, including OSON headers, field dictionaries, and offsets. */
    static EncodedArray encodeArray(List<JsonNode> records, int maxBytes) throws SQLException {
        BoundedOutput output = new BoundedOutput(maxBytes);
        try (OracleJsonGenerator generator = JSON_FACTORY.createJsonBinaryGenerator(output)) {
            generator.writeStartArray();
            for (JsonNode record : records) {
                generator.write(toOracleJsonValue(record));
            }
            generator.writeEnd();
        }
        return new EncodedArray(output.bytes(), output.size);
    }

    /** Bytes are retained only when the complete encoding fits the requested limit. */
    record EncodedArray(byte[] bytes, long size) {}

    private static final class BoundedOutput extends OutputStream {

        private final int maxBytes;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private long size;

        private BoundedOutput(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public void write(int value) {
            size++;
            if (size <= maxBytes) {
                buffer.write(value);
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            size += length;
            if (size <= maxBytes) {
                buffer.write(bytes, offset, length);
            }
        }

        private byte[] bytes() {
            return size <= maxBytes ? buffer.toByteArray() : null;
        }
    }
}
