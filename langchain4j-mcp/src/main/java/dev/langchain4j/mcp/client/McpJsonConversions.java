package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.Internal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts Jackson's tree model into plain JDK values, so that MCP APIs can expose
 * JSON payloads without requiring callers to depend on Jackson.
 */
@Internal
public final class McpJsonConversions {

    private McpJsonConversions() {}

    public static Map<String, Object> toMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        node.properties().forEach(e -> map.put(e.getKey(), toValue(e.getValue())));
        return map;
    }

    public static List<Map<String, Object>> toMaps(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        arrayNode.forEach(item -> result.add(toMap(item)));
        return result;
    }

    public static Object toValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            return toMap(node);
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(item -> values.add(toValue(item)));
            return values;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            if (node.canConvertToInt()) {
                return node.asInt();
            }
            // asLong() silently truncates anything wider than a long
            return node.canConvertToLong() ? node.asLong() : node.bigIntegerValue();
        }
        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }
        return node.asText();
    }
}
