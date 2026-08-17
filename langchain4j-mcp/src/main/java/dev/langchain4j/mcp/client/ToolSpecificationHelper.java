package dev.langchain4j.mcp.client;

import static dev.langchain4j.mcp.client.McpToolMetadataKeys.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ToolSpecificationHelper {

    private static final Logger log = LoggerFactory.getLogger(ToolSpecificationHelper.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<McpIcon>> MCP_ICON_LIST_TYPE = new TypeReference<>() {};
    private static final Set<String> ALLOWED_HEADER_PARAM_TYPES = Set.of("string", "integer", "boolean");

    /**
     * Converts the 'tools' element from a ListToolsResult MCP message
     * to a list of ToolSpecification objects.
     */
    static List<ToolSpecification> toolSpecificationListFromMcpResponse(ArrayNode array) {
        List<ToolSpecification> result = new ArrayList<>();
        for (JsonNode tool : array) {
            String toolName = tool.get("name").asText();
            final ToolSpecification.Builder builder = ToolSpecification.builder();
            builder.name(toolName);
            if (tool.has("description")) {
                builder.description(tool.get("description").asText());
            }
            JsonNode inputSchema = tool.get("inputSchema");
            builder.parameters((JsonObjectSchema) jsonNodeToJsonSchemaElement(inputSchema));
            Map<String, String> paramHeaders = extractAndValidateMcpParamHeaders(inputSchema, toolName);
            if (paramHeaders == null) {
                continue;
            }
            if (!paramHeaders.isEmpty()) {
                builder.addMetadata(MCP_PARAM_HEADERS, paramHeaders);
            }
            if (tool.has("annotations")) {
                processMcpToolAnnotations(tool.get("annotations"), builder);
            }
            if (tool.has("_meta")) {
                processMcpToolMetadata(tool.get("_meta"), builder);
            }
            if (tool.has("title")) {
                builder.addMetadata(TITLE, tool.get("title").asText());
            }
            if (tool.has("outputSchema")) {
                builder.addMetadata(OUTPUT_SCHEMA, OBJECT_MAPPER.convertValue(tool.get("outputSchema"), Object.class));
            }
            if (tool.has("icons")) {
                builder.addMetadata(ICONS, OBJECT_MAPPER.convertValue(tool.get("icons"), MCP_ICON_LIST_TYPE));
            }
            result.add(builder.build());
        }
        return result;
    }

    /**
     * Converts the 'inputSchema' element (inside the 'Tool' type in the MCP schema)
     * to a JsonSchemaElement object that describes the tool's arguments.
     */
    static JsonSchemaElement jsonNodeToJsonSchemaElement(JsonNode node) {
        // MCP SEP-2106 allows composition keywords such as anyOf alongside type "object", and the tool
        // inputSchema root is always type "object". JsonObjectSchema cannot represent a schema-level anyOf,
        // so an object-typed node is parsed as an object (its anyOf constraint is not carried over) rather
        // than as a JsonAnyOfSchema that the root would then fail to cast.
        if (node.has("anyOf") && !isObjectType(node)) {
            JsonAnyOfSchema.Builder anyOf = JsonAnyOfSchema.builder();
            JsonSchemaElement[] types = StreamSupport.stream(node.get("anyOf").spliterator(), false)
                    .map(ToolSpecificationHelper::jsonNodeToJsonSchemaElement)
                    .toArray(JsonSchemaElement[]::new);
            anyOf.anyOf(types);
            if (node.has("description")) {
                anyOf.description(node.get("description").asText());
            }
            return anyOf.build();
        }
        if (node.has("$ref")) {
            String referenceKey = extractReferenceKey(node.get("$ref").asText());
            if (referenceKey != null) {
                return JsonReferenceSchema.builder().reference(referenceKey).build();
            }
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
            // Handle $defs (draft 2019-09+) and definitions (draft-07)
            JsonNode defsNode = node.has("$defs") ? node.get("$defs") : node.get("definitions");
            if (defsNode != null) {
                Map<String, JsonSchemaElement> definitions = new LinkedHashMap<>();
                for (Map.Entry<String, JsonNode> entry : ((ObjectNode) defsNode).properties()) {
                    definitions.put(entry.getKey(), jsonNodeToJsonSchemaElement(entry.getValue()));
                }
                builder.definitions(definitions);
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
                if (node.has("items")) {
                    // if 'items' is an empty array, or missing altogether,
                    // we leave the "items" field unset,
                    // which means it will be serialized as "items": {},
                    // which means "any value"
                    if (!node.get("items").isArray()
                            || (node.get("items").isArray()
                                    && !node.get("items").isEmpty())) {
                        builder.items(jsonNodeToJsonSchemaElement(node.get("items")));
                    }
                }
                return builder.build();
            } else if (nodeType.equals("null")) {
                return new JsonNullSchema();
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

    private static boolean isObjectType(JsonNode node) {
        JsonNode typeNode = node.get("type");
        return typeNode != null
                && typeNode.getNodeType() == JsonNodeType.STRING
                && typeNode.asText().equals("object");
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

    /**
     * Extracts the definition key from a JSON Schema $ref that targets a definition.
     * For example, "#/$defs/Foo" returns "Foo" and "#/definitions/Bar" returns "Bar".
     * Returns {@code null} for any other $ref (for example a pointer into the schema body),
     * which is not a definition key.
     */
    private static String extractReferenceKey(String ref) {
        if (ref.startsWith("#/$defs/")) {
            return ref.substring("#/$defs/".length());
        }
        if (ref.startsWith("#/definitions/")) {
            return ref.substring("#/definitions/".length());
        }
        return null;
    }

    private static String[] toStringArray(ArrayNode jsonArray) {
        String[] result = new String[jsonArray.size()];
        for (int i = 0; i < jsonArray.size(); i++) {
            result[i] = jsonArray.get(i).asText();
        }
        return result;
    }

    private static void processMcpToolAnnotations(JsonNode annotations, ToolSpecification.Builder builder) {
        if (annotations.has(DESTRUCTIVE_HINT)) {
            builder.addMetadata(
                    DESTRUCTIVE_HINT, annotations.get(DESTRUCTIVE_HINT).asBoolean());
        }
        if (annotations.has(IDEMPOTENT_HINT)) {
            builder.addMetadata(
                    IDEMPOTENT_HINT, annotations.get(IDEMPOTENT_HINT).asBoolean());
        }
        if (annotations.has(OPEN_WORLD_HINT)) {
            builder.addMetadata(
                    OPEN_WORLD_HINT, annotations.get(OPEN_WORLD_HINT).asBoolean());
        }
        if (annotations.has(READ_ONLY_HINT)) {
            builder.addMetadata(READ_ONLY_HINT, annotations.get(READ_ONLY_HINT).asBoolean());
        }
        // note that the TITLE_ANNOTATION constant doesn't contain 'title' to disambiguate it with the other title that
        // is
        // stored directly in the Tool object
        if (annotations.has("title")) {
            builder.addMetadata(TITLE_ANNOTATION, annotations.get("title").asText());
        }
    }

    private static void processMcpToolMetadata(JsonNode meta, ToolSpecification.Builder builder) {
        for (Map.Entry<String, JsonNode> property : meta.properties()) {
            // convert the value to a nested Map (independent of Jackson) and store it in the metadata
            builder.addMetadata(property.getKey(), OBJECT_MAPPER.convertValue(property.getValue(), Object.class));
        }
    }

    static Map<String, String> extractAndValidateMcpParamHeaders(JsonNode schema, String toolName) {
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> seenHeaderNamesLower = new HashSet<>();
        List<String> errors = new ArrayList<>();
        extractAndValidateMcpParamHeaders(schema, "", result, seenHeaderNamesLower, errors);
        if (!errors.isEmpty()) {
            for (String error : errors) {
                log.warn("Excluding tool '{}' from tools/list: {}", toolName, error);
            }
            return null;
        }
        return result;
    }

    private static final List<String> FORBIDDEN_SCHEMA_KEYWORDS = List.of(
            "items", "prefixItems", "additionalProperties", "oneOf", "anyOf", "allOf", "not", "if", "then", "else");

    private static void extractAndValidateMcpParamHeaders(
            JsonNode schema,
            String pathPrefix,
            Map<String, String> result,
            Set<String> seenHeaderNamesLower,
            List<String> errors) {
        checkForbiddenSubtrees(schema, errors);
        JsonNode properties = schema.path("properties");
        if (properties.isMissingNode() || !properties.isObject()) {
            return;
        }
        for (Map.Entry<String, JsonNode> entry : properties.properties()) {
            JsonNode propSchema = entry.getValue();
            String propertyPath = pathPrefix.isEmpty() ? entry.getKey() : pathPrefix + "." + entry.getKey();
            JsonNode headerAnnotation = propSchema.get("x-mcp-header");
            if (headerAnnotation != null) {
                if (!headerAnnotation.isTextual()) {
                    errors.add("x-mcp-header value must be a string, but property '" + propertyPath + "' declares "
                            + headerAnnotation.getNodeType().name().toLowerCase());
                } else {
                    validateMcpParamHeader(
                            headerAnnotation.asText(), propSchema, propertyPath, result, seenHeaderNamesLower, errors);
                }
            }
            if (propSchema.has("properties")) {
                extractAndValidateMcpParamHeaders(propSchema, propertyPath, result, seenHeaderNamesLower, errors);
            } else {
                checkForbiddenSubtrees(propSchema, errors);
            }
        }
    }

    private static void validateMcpParamHeader(
            String headerName,
            JsonNode propSchema,
            String propertyPath,
            Map<String, String> result,
            Set<String> seenHeaderNamesLower,
            List<String> errors) {
        if (headerName.isEmpty()) {
            errors.add("x-mcp-header value must not be empty (property '" + propertyPath + "')");
        } else if (!isValidToken(headerName)) {
            errors.add("x-mcp-header value '" + headerName + "' is not a valid HTTP token (property '" + propertyPath
                    + "')");
        }
        if (!seenHeaderNamesLower.add(headerName.toLowerCase())) {
            errors.add("duplicate x-mcp-header value '" + headerName + "' (case-insensitive, property '" + propertyPath
                    + "')");
        }
        for (String type : declaredTypes(propSchema)) {
            if (!ALLOWED_HEADER_PARAM_TYPES.contains(type)) {
                errors.add("x-mcp-header on property '" + propertyPath + "' with forbidden type '" + type
                        + "' (only string, integer, boolean are allowed)");
            }
        }
        if (errors.isEmpty()) {
            result.put(propertyPath, headerName);
        }
    }

    /**
     * JSON Schema allows "type" to be either a single name or an array of names,
     * so a header-carrying property has to be checked against every declared type.
     * "null" is skipped: it only marks the property as optional.
     */
    private static List<String> declaredTypes(JsonNode propSchema) {
        JsonNode type = propSchema.get("type");
        if (type == null) {
            return List.of();
        }
        List<String> types = new ArrayList<>();
        if (type.isTextual()) {
            types.add(type.asText());
        } else if (type.isArray()) {
            for (JsonNode t : type) {
                if (t.isTextual()) {
                    types.add(t.asText());
                }
            }
        }
        types.remove("null");
        return types;
    }

    private static void checkForbiddenSubtrees(JsonNode schema, List<String> errors) {
        for (String keyword : FORBIDDEN_SCHEMA_KEYWORDS) {
            JsonNode node = schema.get(keyword);
            if (node != null && containsHeaderAnnotation(node)) {
                errors.add("x-mcp-header found inside '" + keyword
                        + "' (annotations must be statically reachable via properties keys only)");
            }
        }
    }

    private static boolean containsHeaderAnnotation(JsonNode node) {
        if (node.isObject()) {
            if (node.has("x-mcp-header")) {
                return true;
            }
            for (JsonNode child : node) {
                if (containsHeaderAnnotation(child)) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsHeaderAnnotation(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isValidToken(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!isTchar(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTchar(char c) {
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
            return true;
        }
        return "!#$%&'*+-.^_`|~".indexOf(c) >= 0;
    }
}
