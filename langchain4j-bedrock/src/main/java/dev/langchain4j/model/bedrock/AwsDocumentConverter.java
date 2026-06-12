package dev.langchain4j.model.bedrock;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.document.internal.MapDocument;

@Internal
class AwsDocumentConverter {

    static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().disable(INDENT_OUTPUT).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private AwsDocumentConverter() {}

    public static String documentToJson(Document document) {
        if (document == null) {
            return "{}";
        }

        try {
            Map<String, Object> actualValues = new HashMap<>();
            for (Map.Entry<String, Document> entry : document.asMap().entrySet()) {
                Document doc = entry.getValue();
                actualValues.put(entry.getKey(), documentToObject(doc));
            }
            return OBJECT_MAPPER.writeValueAsString(actualValues);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object documentToObject(Document doc) {
        if (doc.isNumber()) {
            return doc.asNumber();
        } else if (doc.isBoolean()) {
            return doc.asBoolean();
        } else if (doc.isList()) {
            return doc.asList().stream()
                    .map(AwsDocumentConverter::documentToObject)
                    .toList();
        } else if (doc.isMap()) {
            Map<String, Object> innerObject = new HashMap<>();
            doc.asMap().forEach((k, v) -> innerObject.put(k, documentToObject(v)));
            return innerObject;
        } else if (doc.isNull()) {
            return null;
        } else {
            return doc.asString();
        }
    }

    public static Document documentFromJson(String json) {
        try {
            JsonNode jsonNode = OBJECT_MAPPER.readValue(json, JsonNode.class);
            Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
            return new MapDocument(fieldsToDocumentMap(fields));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Document> fieldsToDocumentMap(Iterator<Map.Entry<String, JsonNode>> fields) {
        Map<String, Document> documentMap = new HashMap<>();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = entry.getValue();
            Document doc = getDocument(value);
            documentMap.put(entry.getKey(), doc);
        }
        return documentMap;
    }

    private static Document getDocument(JsonNode value) {
        Document doc;
        if (value.isBoolean()) {
            doc = Document.fromBoolean(value.asBoolean());
        } else if (value.isDouble() || value.isFloat() || value.isBigDecimal()) {
            doc = Document.fromNumber(value.asDouble());
        } else if (value.isInt() || value.isLong() || value.isShort() || value.isBigInteger()) {
            doc = Document.fromNumber(value.asInt());
        } else if (value.isArray()) {
            List<Document> list = new ArrayList<>();
            for (JsonNode node : value) {
                list.add(getDocument(node));
            }
            doc = Document.fromList(list);
        } else if (value.isObject() || value.isPojo()) {
            doc = Document.fromMap(fieldsToDocumentMap(value.fields()));
        } else {
            doc = Document.fromString(value.asText());
        }
        return doc;
    }

    public static Document convertJsonObjectSchemaToDocument(ToolSpecification toolSpecification) {
        return convertJsonObjectSchemaToDocument(toolSpecification, false);
    }

    public static Document convertJsonObjectSchemaToDocument(ToolSpecification toolSpecification, boolean strict) {
        Map<String, Object> schemaMap;

        if (toolSpecification.parameters() == null) {
            schemaMap = new LinkedHashMap<>();
            schemaMap.put("type", "object");

            if (strict) {
                schemaMap.put("properties", Map.of());
                schemaMap.put("required", List.of());
                schemaMap.put("additionalProperties", false);
            }
        } else {
            if (strict && containsRecursiveDefinitions(toolSpecification.parameters())) {
                throw new IllegalArgumentException(
                        "Amazon Bedrock strict tool use does not support recursive JSON schemas. "
                                + "Disable strict mode for this tool with ToolSpecification.strict(false).");
            }
            schemaMap = JsonSchemaElementUtils.toMap(toolSpecification.parameters(), strict);
        }

        try {
            String jsonSchema = OBJECT_MAPPER.writeValueAsString(schemaMap);
            return documentFromJson(jsonSchema);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert schema to Document", e);
        }
    }

    private static boolean containsRecursiveDefinitions(JsonObjectSchema rootSchema) {
        Map<String, JsonSchemaElement> definitions = rootSchema.definitions();
        if (definitions == null || definitions.isEmpty()) {
            return false;
        }

        return definitions.entrySet().stream()
                .anyMatch(
                        entry -> referencesDefinition(entry.getValue(), entry.getKey(), definitions, new HashSet<>()));
    }

    private static boolean referencesDefinition(
            JsonSchemaElement element,
            String targetReference,
            Map<String, JsonSchemaElement> definitions,
            Set<String> visitedReferences) {
        if (element instanceof JsonReferenceSchema referenceSchema) {
            String reference = referenceSchema.reference();
            if (targetReference.equals(reference)) {
                return true;
            }
            if (!visitedReferences.add(reference)) {
                return false;
            }
            JsonSchemaElement definition = definitions.get(reference);
            return definition != null
                    && referencesDefinition(definition, targetReference, definitions, visitedReferences);
        }

        if (element instanceof JsonObjectSchema objectSchema) {
            return objectSchema.properties().values().stream()
                    .anyMatch(property -> referencesDefinition(
                            property, targetReference, definitions, new HashSet<>(visitedReferences)));
        }

        if (element instanceof JsonArraySchema arraySchema && arraySchema.items() != null) {
            return referencesDefinition(arraySchema.items(), targetReference, definitions, visitedReferences);
        }

        if (element instanceof JsonAnyOfSchema anyOfSchema) {
            return anyOfSchema.anyOf().stream()
                    .anyMatch(item ->
                            referencesDefinition(item, targetReference, definitions, new HashSet<>(visitedReferences)));
        }

        return false;
    }

    public static Document convertAdditionalModelRequestFields(Map<String, Object> additionalModelRequestFields) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(additionalModelRequestFields);
            return documentFromJson(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert additionalModelRequestFields to Document", e);
        }
    }
}
