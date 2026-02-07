package dev.langchain4j.model.bedrock;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import software.amazon.awssdk.core.document.Document;

/**
 * Utility class to convert langchain4j {@link JsonSchema} to AWS {@link Document} format
 * for use with Bedrock's structured output feature.
 */
@Internal
class BedrockSchemaMapper {

    private BedrockSchemaMapper() {}

    /**
     * Convert a langchain4j JsonSchema to an AWS Document.
     *
     * @param jsonSchema the JSON schema to convert
     * @return the AWS Document representation of the schema
     */
    static Document fromJsonSchemaToDocument(JsonSchema jsonSchema) {
        if (jsonSchema == null) {
            return null;
        }
        return fromJsonSchemaElementToDocument(jsonSchema.rootElement());
    }

    /**
     * Convert a langchain4j JsonSchemaElement to an AWS Document.
     *
     * @param element the JSON schema element to convert
     * @return the AWS Document representation of the element
     */
    static Document fromJsonSchemaElementToDocument(JsonSchemaElement element) {
        if (element == null) {
            return null;
        }

        Map<String, Document> schemaMap = new HashMap<>();

        if (element instanceof JsonStringSchema stringSchema) {
            schemaMap.put("type", Document.fromString("string"));
            if (stringSchema.description() != null) {
                schemaMap.put("description", Document.fromString(stringSchema.description()));
            }
        } else if (element instanceof JsonBooleanSchema booleanSchema) {
            schemaMap.put("type", Document.fromString("boolean"));
            if (booleanSchema.description() != null) {
                schemaMap.put("description", Document.fromString(booleanSchema.description()));
            }
        } else if (element instanceof JsonNumberSchema numberSchema) {
            schemaMap.put("type", Document.fromString("number"));
            if (numberSchema.description() != null) {
                schemaMap.put("description", Document.fromString(numberSchema.description()));
            }
        } else if (element instanceof JsonIntegerSchema integerSchema) {
            schemaMap.put("type", Document.fromString("integer"));
            if (integerSchema.description() != null) {
                schemaMap.put("description", Document.fromString(integerSchema.description()));
            }
        } else if (element instanceof JsonEnumSchema enumSchema) {
            schemaMap.put("type", Document.fromString("string"));
            if (enumSchema.description() != null) {
                schemaMap.put("description", Document.fromString(enumSchema.description()));
            }
            if (enumSchema.enumValues() != null && !enumSchema.enumValues().isEmpty()) {
                List<Document> enumDocs = enumSchema.enumValues().stream()
                        .map(Document::fromString)
                        .collect(Collectors.toList());
                schemaMap.put("enum", Document.fromList(enumDocs));
            }
        } else if (element instanceof JsonObjectSchema objectSchema) {
            schemaMap.put("type", Document.fromString("object"));
            if (objectSchema.description() != null) {
                schemaMap.put("description", Document.fromString(objectSchema.description()));
            }
            if (objectSchema.properties() != null && !objectSchema.properties().isEmpty()) {
                Map<String, Document> propertiesMap = new HashMap<>();
                for (Map.Entry<String, JsonSchemaElement> entry :
                        objectSchema.properties().entrySet()) {
                    propertiesMap.put(entry.getKey(), fromJsonSchemaElementToDocument(entry.getValue()));
                }
                schemaMap.put("properties", Document.fromMap(propertiesMap));
            }
            if (objectSchema.required() != null && !objectSchema.required().isEmpty()) {
                List<Document> requiredDocs = objectSchema.required().stream()
                        .map(Document::fromString)
                        .collect(Collectors.toList());
                schemaMap.put("required", Document.fromList(requiredDocs));
            }
        } else if (element instanceof JsonArraySchema arraySchema) {
            schemaMap.put("type", Document.fromString("array"));
            if (arraySchema.description() != null) {
                schemaMap.put("description", Document.fromString(arraySchema.description()));
            }
            if (arraySchema.items() != null) {
                schemaMap.put("items", fromJsonSchemaElementToDocument(arraySchema.items()));
            }
        } else if (element instanceof JsonAnyOfSchema anyOfSchema) {
            if (anyOfSchema.description() != null) {
                schemaMap.put("description", Document.fromString(anyOfSchema.description()));
            }
            if (anyOfSchema.anyOf() != null && !anyOfSchema.anyOf().isEmpty()) {
                List<Document> anyOfDocs = anyOfSchema.anyOf().stream()
                        .map(BedrockSchemaMapper::fromJsonSchemaElementToDocument)
                        .collect(Collectors.toList());
                schemaMap.put("anyOf", Document.fromList(anyOfDocs));
            }
        } else if (element instanceof JsonNullSchema) {
            schemaMap.put("type", Document.fromString("null"));
        } else if (element instanceof JsonRawSchema rawSchema) {
            // For raw JSON schema, parse and convert the raw schema string to Document
            return AwsDocumentConverter.documentFromJson(rawSchema.schema());
        } else {
            throw new IllegalArgumentException("Unsupported JsonSchemaElement type: " + element.getClass());
        }

        return Document.fromMap(schemaMap);
    }
}
