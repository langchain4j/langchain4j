package dev.langchain4j.model.bedrock;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.document.internal.MapDocument;

@Internal
class AwsDocumentConverter {

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private AwsDocumentConverter() {}

    public static String documentToJson(Document document) {
        try {
            Map<String, Object> actualValues = new HashMap<>();
            for (Map.Entry<String, Document> entry : document.asMap().entrySet()) {
                Document doc = entry.getValue();
                actualValues.put(entry.getKey(), documentToObject(doc));
                // Add other types as needed
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
        } else {
            return doc.asString();
        }
    }

    public static Document documentFromJson(String json) {
        try {
            final JsonNode jsonNode = OBJECT_MAPPER.readValue(json, JsonNode.class);
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
            final JsonNode value = entry.getValue();
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
        // Convert ToolSpecification to a Map using JsonSchemaElementHelper
        Map<String, Object> schemaMap = new HashMap<>();
        schemaMap.put("type", "object");

        if (toolSpecification.description() != null) {
            schemaMap.put("description", toolSpecification.description());
        }

        if (toolSpecification.parameters() != null) {
            Map<String, Map<String, Object>> propertiesMap =
                    JsonSchemaElementUtils.toMap(toolSpecification.parameters().properties());
            schemaMap.put("properties", propertiesMap);

            List<String> required =
                    new ArrayList<>(toolSpecification.parameters().properties().keySet());
            schemaMap.put("required", required);
        }

        // Convert the schema map to AWS Document
        try {
            String jsonSchema = OBJECT_MAPPER.writeValueAsString(schemaMap);
            return documentFromJson(jsonSchema);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert schema to Document", e);
        }
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
