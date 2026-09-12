package dev.langchain4j.model.bedrock;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.document.internal.MapDocument;

@Internal
class AwsDocumentConverter {

    private static final Json.JsonCodec CODEC =
            ProviderJson.codec(ProviderJsonSpec.builder().build());

    private AwsDocumentConverter() {}

    public static String documentToJson(Document document) {
        if (document == null) {
            return "{}";
        }

        Map<String, Object> actualValues = new HashMap<>();
        for (Map.Entry<String, Document> entry : document.asMap().entrySet()) {
            Document doc = entry.getValue();
            actualValues.put(entry.getKey(), documentToObject(doc));
        }
        return CODEC.toJson(actualValues);
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
        return new MapDocument(fieldsToDocumentMap(CODEC.fromJson(json, Map.class)));
    }

    private static Map<String, Document> fieldsToDocumentMap(Map<?, ?> fields) {
        Map<String, Document> documentMap = new HashMap<>();
        fields.forEach((key, value) -> documentMap.put(String.valueOf(key), getDocument(value)));
        return documentMap;
    }

    private static Document getDocument(Object value) {
        if (value == null) {
            return Document.fromNull();
        } else if (value instanceof Boolean bool) {
            return Document.fromBoolean(bool);
        } else if (value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
            return Document.fromNumber(((Number) value).doubleValue());
        } else if (value instanceof BigInteger bigInteger) {
            return Document.fromNumber(bigInteger);
        } else if (value instanceof Number number) {
            return Document.fromNumber(BigInteger.valueOf(number.longValue()));
        } else if (value instanceof List<?> list) {
            List<Document> documents = new ArrayList<>(list.size());
            for (Object element : list) {
                documents.add(getDocument(element));
            }
            return Document.fromList(documents);
        } else if (value instanceof Map<?, ?> map) {
            return Document.fromMap(fieldsToDocumentMap(map));
        } else {
            return Document.fromString(String.valueOf(value));
        }
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

        return documentFromJson(CODEC.toJson(schemaMap));
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
        return documentFromJson(CODEC.toJson(additionalModelRequestFields));
    }
}
