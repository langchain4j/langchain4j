package dev.langchain4j.mcp.client;

import static dev.langchain4j.mcp.client.McpToolMetadataKeys.*;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.transport.McpJson;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ToolSpecificationHelper {

    private static final Logger log = LoggerFactory.getLogger(ToolSpecificationHelper.class);
    private static final Set<String> ALLOWED_HEADER_PARAM_TYPES = Set.of("string", "integer", "boolean");

    /**
     * Converts the 'tools' element from a ListToolsResult MCP message
     * to a list of ToolSpecification objects.
     */
    static List<ToolSpecification> toolSpecificationListFromMcpResponse(List<Map<String, Object>> tools) {
        List<ToolSpecification> result = new ArrayList<>();
        for (Map<String, Object> tool : tools) {
            String toolName = string(tool.get("name"));
            final ToolSpecification.Builder builder = ToolSpecification.builder();
            builder.name(toolName);
            if (tool.containsKey("description")) {
                builder.description(string(tool.get("description")));
            }
            Map<String, Object> inputSchema = object(tool.get("inputSchema"));
            builder.parameters((JsonObjectSchema) jsonNodeToJsonSchemaElement(inputSchema));
            Map<String, String> paramHeaders = extractAndValidateMcpParamHeaders(inputSchema, toolName);
            if (paramHeaders == null) {
                continue;
            }
            if (!paramHeaders.isEmpty()) {
                builder.addMetadata(MCP_PARAM_HEADERS, paramHeaders);
            }
            if (tool.containsKey("annotations")) {
                processMcpToolAnnotations(object(tool.get("annotations")), builder);
            }
            if (tool.containsKey("_meta")) {
                processMcpToolMetadata(object(tool.get("_meta")), builder);
            }
            if (tool.containsKey("title")) {
                builder.addMetadata(TITLE, string(tool.get("title")));
            }
            if (tool.containsKey("outputSchema")) {
                builder.addMetadata(OUTPUT_SCHEMA, tool.get("outputSchema"));
            }
            if (tool.containsKey("icons")) {
                builder.addMetadata(ICONS, toIcons(tool.get("icons")));
            }
            result.add(builder.build());
        }
        return result;
    }

    /**
     * Converts the 'inputSchema' element (inside the 'Tool' type in the MCP schema)
     * to a JsonSchemaElement object that describes the tool's arguments.
     */
    static JsonSchemaElement jsonNodeToJsonSchemaElement(Map<String, Object> node) {
        return jsonNodeToJsonSchemaElement(node, Map.of(), new HashSet<>());
    }

    /**
     * @param definitions   the '$defs'/'definitions' collected from this node and all of its ancestors,
     *                       keyed by name, used to resolve a '$ref' that appears inside an 'allOf'
     * @param resolvingRefs the definition keys currently being resolved, used to break reference cycles
     */
    private static JsonSchemaElement jsonNodeToJsonSchemaElement(
            Map<String, Object> node, Map<String, Object> definitions, Set<String> resolvingRefs) {
        // MCP SEP-2106 allows composition keywords such as anyOf alongside type "object", and the tool
        // inputSchema root is always type "object". JsonObjectSchema cannot represent a schema-level anyOf,
        // so an object-typed node is parsed as an object (its anyOf constraint is not carried over) rather
        // than as a JsonAnyOfSchema that the root would then fail to cast.
        if (node.containsKey("anyOf") && !isObjectType(node)) {
            JsonAnyOfSchema.Builder anyOf = JsonAnyOfSchema.builder();
            JsonSchemaElement[] types = array(node.get("anyOf")).stream()
                    .map(item -> jsonNodeToJsonSchemaElement(object(item), definitions, resolvingRefs))
                    .toArray(JsonSchemaElement[]::new);
            anyOf.anyOf(types);
            if (node.containsKey("description")) {
                anyOf.description(string(node.get("description")));
            }
            return anyOf.build();
        }
        if (node.containsKey("$ref")) {
            String referenceKey = extractReferenceKey(string(node.get("$ref")));
            if (referenceKey != null) {
                return JsonReferenceSchema.builder().reference(referenceKey).build();
            }
        }
        Object typeNode = node.get("type");
        // If no type is specified, default to object schema
        if (typeNode == null || "object".equals(typeNode)) {
            JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
            if (node.containsKey("description")) {
                builder.description(string(node.get("description")));
            }
            // Definitions ('$defs' in draft 2019-09+, 'definitions' in draft-07) declared on this node are
            // added to those inherited from ancestor schemas and threaded further down, so a '$ref' (e.g.
            // one appearing inside 'allOf') resolves even when its definition lives on an outer schema.
            Object defsNode = node.containsKey("$defs") ? node.get("$defs") : node.get("definitions");
            Map<String, Object> availableDefinitions = definitions;
            if (!object(defsNode).isEmpty()) {
                availableDefinitions = new LinkedHashMap<>(definitions);
                availableDefinitions.putAll(object(defsNode));
            }
            // MCP servers commonly compose object schemas with 'allOf' (e.g. one object schema per
            // capability). JsonObjectSchema cannot represent 'allOf' itself, so object-typed sub-schemas
            // (including those reached through a '$ref') are merged into this one: union of properties,
            // union of required and collected definitions, with the node's own members taking precedence
            // on conflicts. An 'allOf' entry that does not resolve to an object (a scalar sub-schema, or a
            // '$ref' that cannot be resolved or points at a non-object) is skipped with a warning.
            Map<String, JsonSchemaElement> mergedProperties = new LinkedHashMap<>();
            Set<String> mergedRequired = new LinkedHashSet<>();
            Map<String, JsonSchemaElement> mergedDefinitions = new LinkedHashMap<>();
            if (node.containsKey("allOf")) {
                for (Object allOfEntry : array(node.get("allOf"))) {
                    mergeAllOfSubSchema(
                            object(allOfEntry),
                            availableDefinitions,
                            resolvingRefs,
                            mergedProperties,
                            mergedRequired,
                            mergedDefinitions);
                }
            }
            if (node.containsKey("properties")) {
                for (Map.Entry<String, Object> property :
                        object(node.get("properties")).entrySet()) {
                    mergedProperties.put(
                            property.getKey(),
                            jsonNodeToJsonSchemaElement(
                                    object(property.getValue()), availableDefinitions, resolvingRefs));
                }
            }
            if (!mergedProperties.isEmpty()) {
                builder.addProperties(mergedProperties);
            }
            if (node.containsKey("required")) {
                mergedRequired.addAll(List.of(toStringArray(node.get("required"))));
            }
            if (!mergedRequired.isEmpty()) {
                builder.required(List.copyOf(mergedRequired));
            }
            if (node.containsKey("additionalProperties")) {
                Object additionalProperties = node.get("additionalProperties");
                if (additionalProperties instanceof Map) {
                    // A schema-typed additionalProperties (e.g. {"type": "string"}) means additional
                    // properties ARE allowed, just constrained to that schema. JsonObjectSchema only
                    // models this as a boolean, so it must be treated as "allowed" (true) rather than
                    // being collapsed to false, which would wrongly tell the model to reject every
                    // extra property.
                    builder.additionalProperties(true);
                } else {
                    builder.additionalProperties(bool(additionalProperties));
                }
            }
            // Convert the definitions collected above (draft 2019-09+ '$defs', draft-07 'definitions')
            // into the schema's own 'definitions' map.
            if (defsNode != null) {
                for (Map.Entry<String, Object> entry : object(defsNode).entrySet()) {
                    mergedDefinitions.put(
                            entry.getKey(),
                            jsonNodeToJsonSchemaElement(object(entry.getValue()), availableDefinitions, resolvingRefs));
                }
            }
            if (!mergedDefinitions.isEmpty()) {
                builder.definitions(mergedDefinitions);
            }
            return builder.build();
        } else if (typeNode instanceof String) {
            String nodeType = (String) typeNode;
            if (nodeType.equals("string")) {
                if (node.containsKey("enum")) {
                    JsonEnumSchema.Builder builder = JsonEnumSchema.builder();
                    if (node.containsKey("description")) {
                        builder.description(string(node.get("description")));
                    }
                    builder.enumValues(toStringArray(node.get("enum")));
                    return builder.build();
                } else {
                    JsonStringSchema.Builder builder = JsonStringSchema.builder();
                    if (node.containsKey("description")) {
                        builder.description(string(node.get("description")));
                    }
                    return builder.build();
                }
            } else if (nodeType.equals("number")) {
                JsonNumberSchema.Builder builder = JsonNumberSchema.builder();
                if (node.containsKey("description")) {
                    builder.description(string(node.get("description")));
                }
                return builder.build();
            } else if (nodeType.equals("integer")) {
                JsonIntegerSchema.Builder builder = JsonIntegerSchema.builder();
                if (node.containsKey("description")) {
                    builder.description(string(node.get("description")));
                }
                return builder.build();
            } else if (nodeType.equals("boolean")) {
                JsonBooleanSchema.Builder builder = JsonBooleanSchema.builder();
                if (node.containsKey("description")) {
                    builder.description(string(node.get("description")));
                }
                return builder.build();
            } else if (nodeType.equals("array")) {
                JsonArraySchema.Builder builder = JsonArraySchema.builder();
                if (node.containsKey("description")) {
                    builder.description(string(node.get("description")));
                }
                if (node.containsKey("items")) {
                    // if 'items' is an empty array, or missing altogether,
                    // we leave the "items" field unset,
                    // which means it will be serialized as "items": {},
                    // which means "any value"
                    Object items = node.get("items");
                    if (!(items instanceof List) || !((List<?>) items).isEmpty()) {
                        builder.items(jsonNodeToJsonSchemaElement(object(items), definitions, resolvingRefs));
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
            JsonSchemaElement[] types = array(typeNode).stream()
                    .map(ToolSpecificationHelper::toTypeElement)
                    .toArray(JsonSchemaElement[]::new);
            anyOf.anyOf(types);
            if (node.containsKey("description")) {
                anyOf.description(string(node.get("description")));
            }
            return anyOf.build();
        }
    }

    /**
     * Merges a single 'allOf' sub-schema into the accumulators of the containing object schema.
     * A '$ref' entry is resolved against {@code definitions} (guarding against reference cycles);
     * any entry that does not resolve to a {@link JsonObjectSchema} cannot be merged and is skipped
     * with a warning, since JsonObjectSchema can only absorb object-typed sub-schemas.
     */
    private static void mergeAllOfSubSchema(
            Map<String, Object> entry,
            Map<String, Object> definitions,
            Set<String> resolvingRefs,
            Map<String, JsonSchemaElement> mergedProperties,
            Set<String> mergedRequired,
            Map<String, JsonSchemaElement> mergedDefinitions) {
        if (entry.containsKey("$ref")) {
            String ref = string(entry.get("$ref"));
            String refKey = extractReferenceKey(ref);
            if (refKey == null || !definitions.containsKey(refKey)) {
                log.warn(
                        "Ignoring 'allOf' entry with unresolvable $ref '{}' while converting a tool input schema:"
                                + " no matching definition was found",
                        ref);
                return;
            }
            if (!resolvingRefs.add(refKey)) {
                // This $ref is already being resolved higher up the stack (a cyclic definition); its
                // members are merged at that outer level, so the repeat is skipped to avoid looping.
                return;
            }
            try {
                JsonSchemaElement resolved =
                        jsonNodeToJsonSchemaElement(object(definitions.get(refKey)), definitions, resolvingRefs);
                if (!mergeObjectSchema(resolved, mergedProperties, mergedRequired, mergedDefinitions)) {
                    log.warn(
                            "Ignoring 'allOf' $ref '{}' while converting a tool input schema: it resolves to a"
                                    + " non-object schema, which cannot be merged into the containing object",
                            ref);
                }
            } finally {
                resolvingRefs.remove(refKey);
            }
        } else {
            JsonSchemaElement element = jsonNodeToJsonSchemaElement(entry, definitions, resolvingRefs);
            if (!mergeObjectSchema(element, mergedProperties, mergedRequired, mergedDefinitions)) {
                log.warn(
                        "Ignoring non-object 'allOf' entry (declared type: {}) while converting a tool input schema:"
                                + " only object sub-schemas can be merged into the containing object",
                        entry.get("type"));
            }
        }
    }

    /**
     * Merges an object sub-schema (its properties, required and definitions) into the given accumulators.
     * Returns {@code false} without touching them when the element is not a {@link JsonObjectSchema}.
     */
    private static boolean mergeObjectSchema(
            JsonSchemaElement element,
            Map<String, JsonSchemaElement> mergedProperties,
            Set<String> mergedRequired,
            Map<String, JsonSchemaElement> mergedDefinitions) {
        if (element instanceof JsonObjectSchema subSchema) {
            mergedProperties.putAll(subSchema.properties());
            mergedRequired.addAll(subSchema.required());
            mergedDefinitions.putAll(subSchema.definitions());
            return true;
        }
        return false;
    }

    private static boolean isObjectType(Map<String, Object> node) {
        return "object".equals(node.get("type"));
    }

    private static JsonSchemaElement toTypeElement(Object node) {
        if (!(node instanceof String)) {
            throw new IllegalArgumentException(node + " is not a string");
        }
        switch ((String) node) {
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
                throw new IllegalArgumentException("Unsupported type: " + node);
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

    private static String[] toStringArray(Object jsonArray) {
        List<Object> values = array(jsonArray);
        String[] result = new String[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = string(values.get(i));
        }
        return result;
    }

    private static void processMcpToolAnnotations(Map<String, Object> annotations, ToolSpecification.Builder builder) {
        if (annotations.containsKey(DESTRUCTIVE_HINT)) {
            builder.addMetadata(DESTRUCTIVE_HINT, bool(annotations.get(DESTRUCTIVE_HINT)));
        }
        if (annotations.containsKey(IDEMPOTENT_HINT)) {
            builder.addMetadata(IDEMPOTENT_HINT, bool(annotations.get(IDEMPOTENT_HINT)));
        }
        if (annotations.containsKey(OPEN_WORLD_HINT)) {
            builder.addMetadata(OPEN_WORLD_HINT, bool(annotations.get(OPEN_WORLD_HINT)));
        }
        if (annotations.containsKey(READ_ONLY_HINT)) {
            builder.addMetadata(READ_ONLY_HINT, bool(annotations.get(READ_ONLY_HINT)));
        }
        // note that the TITLE_ANNOTATION constant doesn't contain 'title' to disambiguate it with the other title that
        // is
        // stored directly in the Tool object
        if (annotations.containsKey("title")) {
            builder.addMetadata(TITLE_ANNOTATION, string(annotations.get("title")));
        }
    }

    private static void processMcpToolMetadata(Map<String, Object> meta, ToolSpecification.Builder builder) {
        meta.forEach(builder::addMetadata);
    }

    static Map<String, String> extractAndValidateMcpParamHeaders(Map<String, Object> schema, String toolName) {
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
            Map<String, Object> schema,
            String pathPrefix,
            Map<String, String> result,
            Set<String> seenHeaderNamesLower,
            List<String> errors) {
        checkForbiddenSubtrees(schema, errors);
        Map<String, Object> properties = object(schema.get("properties"));
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            // a non-object property schema yields an empty map, which simply declares no header
            Map<String, Object> propSchema = object(entry.getValue());
            String propertyPath = pathPrefix.isEmpty() ? entry.getKey() : pathPrefix + "." + entry.getKey();
            Object headerAnnotation = propSchema.get("x-mcp-header");
            if (headerAnnotation != null) {
                if (!(headerAnnotation instanceof String)) {
                    errors.add("x-mcp-header value must be a string, but property '" + propertyPath + "' declares "
                            + jsonTypeName(headerAnnotation));
                } else {
                    validateMcpParamHeader(
                            (String) headerAnnotation, propSchema, propertyPath, result, seenHeaderNamesLower, errors);
                }
            }
            if (propSchema.containsKey("properties")) {
                extractAndValidateMcpParamHeaders(propSchema, propertyPath, result, seenHeaderNamesLower, errors);
            } else {
                checkForbiddenSubtrees(propSchema, errors);
            }
        }
    }

    private static void validateMcpParamHeader(
            String headerName,
            Map<String, Object> propSchema,
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
        if (!seenHeaderNamesLower.add(headerName.toLowerCase(Locale.ROOT))) {
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
    private static List<String> declaredTypes(Map<String, Object> propSchema) {
        Object type = propSchema.get("type");
        if (type == null) {
            return List.of();
        }
        List<String> types = new ArrayList<>();
        if (type instanceof String) {
            types.add((String) type);
        } else if (type instanceof List) {
            for (Object t : (List<?>) type) {
                if (t instanceof String) {
                    types.add((String) t);
                }
            }
        }
        types.remove("null");
        return types;
    }

    private static void checkForbiddenSubtrees(Map<String, Object> schema, List<String> errors) {
        for (String keyword : FORBIDDEN_SCHEMA_KEYWORDS) {
            Object node = schema.get(keyword);
            if (node != null && containsHeaderAnnotation(node)) {
                errors.add("x-mcp-header found inside '" + keyword
                        + "' (annotations must be statically reachable via properties keys only)");
            }
        }
    }

    private static boolean containsHeaderAnnotation(Object node) {
        if (node instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) node;
            if (map.containsKey("x-mcp-header")) {
                return true;
            }
            for (Object child : map.values()) {
                if (containsHeaderAnnotation(child)) {
                    return true;
                }
            }
        } else if (node instanceof List) {
            for (Object child : (List<?>) node) {
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

    // --- JSON value accessors, over plain JDK types rather than a JSON library ---

    /**
     * Mirrors JsonNode.asBoolean(false), which also accepts the strings "true" and "false".
     */
    private static boolean bool(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0;
        }
        // TextNode.asBoolean() accepts only the exact, trimmed token
        return value instanceof String && "true".equals(((String) value).trim());
    }

    /**
     * Mirrors JsonNode.asText(), which renders scalars but yields "" for objects and arrays
     * rather than their Java toString form.
     */
    private static String string(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map || value instanceof List) {
            return "";
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        // mirrors JsonNode.path(): anything that is not an object is treated as an empty one,
        // so a boolean schema or a tuple-form 'items' degrades instead of failing
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Object value) {
        return value instanceof List ? (List<Object>) value : List.of();
    }

    private static List<McpIcon> toIcons(Object value) {
        return McpJson.convertList(value, McpIcon.class);
    }

    /**
     * Mirrors the JSON type names Jackson reports, so validation messages are unchanged.
     */
    private static String jsonTypeName(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof List) {
            return "array";
        }
        return "object";
    }
}
