package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

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
        if (node.has("anyOf")) {
            JsonAnyOfSchema.Builder anyOf = JsonAnyOfSchema.builder();
            JsonSchemaElement[] types = StreamSupport.stream(node.get("anyOf").spliterator(), false)
                    .map(ToolSpecificationHelper::jsonNodeToJsonSchemaElement)
                    .toArray(JsonSchemaElement[]::new);
            anyOf.anyOf(types);
            return anyOf.build();
        }
        JsonNode typeNode = node.get("type");
        // If no type is specified, default to object schema
        if (typeNode == null
                || (node.get("type").getNodeType() == JsonNodeType.STRING
                        && node.get("type").asText().equals("object"))) {
            JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
            if (node.has("description")) {
                builder.description(node.get("description").asText());
            }
            if (node.has("properties")) {
                ObjectNode propertiesObject = (ObjectNode) node.get("properties");
                for (Map.Entry<String, JsonNode> property : propertiesObject.properties()) {
                    builder.addProperty(property.getKey(), jsonNodeToJsonSchemaElement(property.getValue()));
                }
            }
            if (node.has("required")) {
                builder.required(toStringArray((ArrayNode) node.get("required")));
            }
            if (node.has("additionalProperties")) {
                builder.additionalProperties(node.get("additionalProperties").asBoolean(false));
            }
            return builder.build();
        } else if (node.get("type").getNodeType() == JsonNodeType.STRING) {
            String nodeType = node.get("type").asText();
            if (nodeType.equals("string")) {
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
        } else {
            // this represents an array with multiple allowed types for items
            // for example:
            // "type": "array",
            //  "items": {
            //    "type": ["integer", "string", "null"]
            //  }
            //
            // and we transform this into
            //
            // "type": "array",
            // "items": {
            //   "anyOf": [
            //       {
            //           "type": "integer"
            //       },
            //       {
            //           "type": "string"
            //       },
            //       {
            //           "type": "null"
            //       }
            //   ]
            // }
            JsonAnyOfSchema.Builder anyOf = JsonAnyOfSchema.builder();
            JsonSchemaElement[] types = StreamSupport.stream(node.get("type").spliterator(), false)
                    .map(ToolSpecificationHelper::toTypeElement)
                    .toArray(JsonSchemaElement[]::new);
            anyOf.anyOf(types);
            return anyOf.build();
        }
    }

    private static JsonSchemaElement toTypeElement(JsonNode node) {
        if (!node.isTextual()) {
            throw new IllegalArgumentException(node + " is not a string");
        }
        switch (node.textValue()) {
            case "string":
                return JsonStringSchema.builder().build();
            case "number":
                return JsonNumberSchema.builder().build();
            case "integer":
                return JsonIntegerSchema.builder().build();
            case "boolean":
                return JsonBooleanSchema.builder().build();
            case "array":
                return JsonArraySchema.builder().build();
            case "object":
                return JsonObjectSchema.builder().build();
            case "null":
                return new JsonNullSchema();
            default:
                throw new IllegalArgumentException("Unsupported type: " + node.textValue());
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
