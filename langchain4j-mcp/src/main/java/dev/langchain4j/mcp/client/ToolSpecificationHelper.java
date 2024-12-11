package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ToolSpecificationHelper {

    /**
     * Converts the 'tools' element from a ListToolsResult MCP message
     * to a list of ToolSpecification objects.
     */
    static List<ToolSpecification> toolSpecificationListFromMcpResponse(ArrayNode array) {
        List<ToolSpecification> result = new ArrayList<>();
        for (JsonNode tool : array) {
            final ToolSpecification.Builder builder = ToolSpecification.builder();
            builder.name(tool.get("name").asText());
            if (tool.has("description")) {
                builder.description(tool.get("description").asText());
            }
            builder.parameters((JsonObjectSchema) jsonNodeToJsonSchemaElement(tool.get("inputSchema")));
            result.add(builder.build());
        }
        return result;
    }

    /**
     * Converts the 'inputSchema' element (inside the 'Tool' type in the MCP schema)
     * to a JsonSchemaElement object that describes the tool's arguments.
     */
    static JsonSchemaElement jsonNodeToJsonSchemaElement(JsonNode node) {
        String nodeType = node.get("type").asText();
        if (nodeType.equals("object")) {
            JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
            JsonNode required = node.get("required");
            if (required != null) {
                builder.required(toStringArray((ArrayNode) required));
            }
            if (node.has("additionalProperties")) {
                builder.additionalProperties(node.get("additionalProperties").asBoolean(false));
            }
            JsonNode description = node.get("description");
            if (description != null) {
                builder.description(description.asText());
            }
            JsonNode properties = node.get("properties");
            if (properties != null) {
                ObjectNode propertiesObject = (ObjectNode) properties;
                for (Map.Entry<String, JsonNode> property : propertiesObject.properties()) {
                    builder.addProperty(property.getKey(), jsonNodeToJsonSchemaElement(property.getValue()));
                }
            }
            return builder.build();
        } else if (nodeType.equals("string")) {
            if (node.has("enum")) {
                JsonEnumSchema.Builder builder = JsonEnumSchema.builder();
                if (node.has("description")) {
                    builder.description(node.get("description").asText());
                }
                builder.enumValues(toStringArray((ArrayNode) node.get("enum")));
                return builder.build();
            } else {
                JsonStringSchema.Builder builder = JsonStringSchema.builder();
                if (node.has("description")) {
                    builder.description(node.get("description").asText());
                }
                return builder.build();
            }
        } else if (nodeType.equals("number")) {
            JsonNumberSchema.Builder builder = JsonNumberSchema.builder();
            if (node.has("description")) {
                builder.description(node.get("description").asText());
            }
            return builder.build();
        } else if (nodeType.equals("integer")) {
            JsonIntegerSchema.Builder builder = JsonIntegerSchema.builder();
            if (node.has("description")) {
                builder.description(node.get("description").asText());
            }
            return builder.build();
        } else if (nodeType.equals("boolean")) {
            JsonBooleanSchema.Builder builder = JsonBooleanSchema.builder();
            if (node.has("description")) {
                builder.description(node.get("description").asText());
            }
            return builder.build();
        } else if (nodeType.equals("array")) {
            JsonArraySchema.Builder builder = JsonArraySchema.builder();
            if (node.has("description")) {
                builder.description(node.get("description").asText());
            }
            builder.items(jsonNodeToJsonSchemaElement(node.get("items")));
            return builder.build();
        } else {
            throw new IllegalArgumentException("Unknown element type: " + nodeType);
        }
    }

    private static String[] toStringArray(ArrayNode jsonArray) {
        String[] result = new String[jsonArray.size()];
        for (int i = 0; i < jsonArray.size(); i++) {
            result[i] = jsonArray.get(i).asText();
        }
        return result;
    }
}
