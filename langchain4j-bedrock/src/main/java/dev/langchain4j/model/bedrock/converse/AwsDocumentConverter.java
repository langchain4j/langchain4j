package dev.langchain4j.model.bedrock.converse;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Objects.nonNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.document.internal.MapDocument;

public class AwsDocumentConverter {

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public static final String DESCRIPTION = "description";
    public static final String TYPE = "type";
    public static final String OBJECT = "object";

    private AwsDocumentConverter() {}

    public static String documentToJson(Document document) {
        try {
            Map<String, Object> actualValues = new HashMap<>();
            for (Map.Entry<String, Document> entry : document.asMap().entrySet()) {
                Document doc = entry.getValue();
                if (doc.isNumber()) {
                    actualValues.put(entry.getKey(), doc.asNumber());
                } else if (doc.isString()) {
                    actualValues.put(entry.getKey(), doc.asString());
                } else if (doc.isBoolean()) {
                    actualValues.put(entry.getKey(), doc.asBoolean());
                }
                // Add other types as needed
            }
            return OBJECT_MAPPER.writeValueAsString(actualValues);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static Document documentFromJson(String json) {
        try {
            final JsonNode jsonNode = OBJECT_MAPPER.readValue(json, JsonNode.class);
            Map<String, Document> documentMap = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                final JsonNode value = entry.getValue();
                Document doc = getDocument(value);
                documentMap.put(entry.getKey(), doc);
            }
            return new MapDocument(documentMap);

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
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
        } else {
            doc = Document.fromString(value.asText());
        }
        return doc;
    }

    public static Document convertJsonObjectSchemaToDocument(ToolSpecification toolSpecification) {
        final Document.MapBuilder mapBuilder = Document.mapBuilder().putString(TYPE, OBJECT);
        if (nonNull(toolSpecification.description()))
            mapBuilder.putString(DESCRIPTION, toolSpecification.description());

        if (nonNull(toolSpecification.parameters()))
            mapBuilder
                    .putDocument("properties", createPropertiesDocument(toolSpecification.parameters()))
                    .putList("required", builder -> toolSpecification
                            .parameters()
                            .properties()
                            .keySet()
                            .forEach(builder::addString));
        return mapBuilder.build();
    }

    private static Document createPropertiesDocument(JsonObjectSchema schema) {
        Document.MapBuilder propertiesBuilder = Document.mapBuilder();

        for (Map.Entry<String, JsonSchemaElement> entry : schema.properties().entrySet()) {
            String propertyName = entry.getKey();
            JsonSchemaElement element = entry.getValue();

            Document.MapBuilder mapBuilder = Document.mapBuilder()
                    .putString(TYPE, getTypeFromElement(element))
                    .putString(DESCRIPTION, getOrDefault(getDescriptionFromElement(element), propertyName));
            if (element instanceof JsonEnumSchema enumSchema)
                mapBuilder.putList(
                        "enum",
                        enumSchema.enumValues().stream()
                                .map(Document::fromString)
                                .toList());
            Document propertyDoc = mapBuilder.build();

            propertiesBuilder.putDocument(propertyName, propertyDoc);
        }

        return propertiesBuilder.build();
    }

    private static String getTypeFromElement(JsonSchemaElement element) {
        if (element instanceof JsonNumberSchema) {
            return "number";
        } else if (element instanceof JsonStringSchema) {
            return "string";
        } else if (element instanceof JsonBooleanSchema) {
            return "boolean";
        } else if (element instanceof JsonIntegerSchema) {
            return "integer";
        } else if (element instanceof JsonEnumSchema) {
            return "string";
        }
        throw new IllegalArgumentException("Unsupported schema element type: " + element.getClass());
    }

    private static String getDescriptionFromElement(JsonSchemaElement element) {
        if (element instanceof JsonNumberSchema jsonElement) {
            return jsonElement.description();
        } else if (element instanceof JsonStringSchema jsonElement) {
            return jsonElement.description();
        } else if (element instanceof JsonBooleanSchema jsonElement) {
            return jsonElement.description();
        } else if (element instanceof JsonIntegerSchema jsonElement) {
            return jsonElement.description();
        } else if (element instanceof JsonEnumSchema jsonElement) {
            return jsonElement.description();
        }
        throw new IllegalArgumentException("Unsupported schema element type: " + element.getClass());
    }
}
