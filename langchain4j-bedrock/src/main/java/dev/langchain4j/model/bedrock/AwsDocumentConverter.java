package dev.langchain4j.model.bedrock;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.document.internal.MapDocument;

@Internal
class AwsDocumentConverter {

    private static final Json.JsonCodec CODEC = ProviderJson.codec(ProviderJsonSpec.builder().build());

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
        Map<String, Object> schemaMap = new HashMap<>();
        schemaMap.put("type", "object");

        if (toolSpecification.parameters() != null) {
            Map<String, Map<String, Object>> propertiesMap =
                    JsonSchemaElementUtils.toMap(toolSpecification.parameters().properties());
            schemaMap.put("properties", propertiesMap);

            List<String> required =
                    new ArrayList<>(toolSpecification.parameters().required());
            schemaMap.put("required", required);
        }

        return documentFromJson(CODEC.toJson(schemaMap));
    }

    public static Document convertAdditionalModelRequestFields(Map<String, Object> additionalModelRequestFields) {
        return documentFromJson(CODEC.toJson(additionalModelRequestFields));
    }
}
