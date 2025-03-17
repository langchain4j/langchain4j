package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;

class AwsDocumentConverterTest {

    @Test
    void convert_primitive_types_to_json() {
        // Given
        Map<String, Document> docMap = new HashMap<>();
        docMap.put("string", Document.fromString("test"));
        docMap.put("integer", Document.fromNumber(42));
        docMap.put("double", Document.fromNumber(42.5));
        docMap.put("boolean", Document.fromBoolean(true));
        Document document = Document.fromMap(docMap);

        // When
        String json = AwsDocumentConverter.documentToJson(document);

        // Then
        assertThat(json).containsIgnoringWhitespaces("\"string\": \"test\"");
        assertThat(json).containsIgnoringWhitespaces("\"integer\": 42");
        assertThat(json).containsIgnoringWhitespaces("\"double\": 42.5");
        assertThat(json).containsIgnoringWhitespaces("\"boolean\": true");
    }

    @Test
    void convert_list_to_json() {
        // Given
        List<Document> list =
                Arrays.asList(Document.fromString("item1"), Document.fromNumber(2), Document.fromBoolean(true));
        Map<String, Document> docMap = new HashMap<>();
        docMap.put("list", Document.fromList(list));
        Document document = Document.fromMap(docMap);

        // When
        String json = AwsDocumentConverter.documentToJson(document);

        // Then
        assertThat(json).containsIgnoringWhitespaces("\"list\": [");
        assertThat(json).containsIgnoringWhitespaces("\"item1\"");
        assertThat(json).contains("2");
        assertThat(json).contains("true");
    }

    @Test
    void convert_nested_object_to_json() {
        // Given
        Map<String, Document> innerMap = new HashMap<>();
        innerMap.put("inner_key", Document.fromString("inner_value"));

        Map<String, Document> docMap = new HashMap<>();
        docMap.put("nested", Document.fromMap(innerMap));
        Document document = Document.fromMap(docMap);

        // When
        String json = AwsDocumentConverter.documentToJson(document);

        // Then
        assertThat(json).containsIgnoringWhitespaces("\"nested\": {");
        assertThat(json).containsIgnoringWhitespaces("\"inner_key\": \"inner_value\"");
    }

    @Test
    void convert_json_to_primitive_types() {
        // Given
        String json =
                """
                {
                    "string": "test",
                    "integer": 42,
                    "double": 42.5,
                    "boolean": true
                }
                """;

        // When
        Document document = AwsDocumentConverter.documentFromJson(json);

        // Then
        assertThat(document.asMap().get("string").asString()).isEqualTo("test");
        assertThat(document.asMap().get("integer").asNumber().intValue()).isEqualTo(42);
        assertThat(document.asMap().get("double").asNumber().doubleValue()).isEqualTo(42.5);
        assertThat(document.asMap().get("boolean").asBoolean()).isTrue();
    }

    @Test
    void convert_json_to_list() {
        // Given
        String json =
                """
                {
                    "list": ["item1", 2, true]
                }
                """;

        // When
        Document document = AwsDocumentConverter.documentFromJson(json);

        // Then
        List<Document> list = document.asMap().get("list").asList();
        assertThat(list.get(0).asString()).isEqualTo("item1");
        assertThat(list.get(1).asNumber().intValue()).isEqualTo(2);
        assertThat(list.get(2).asBoolean()).isTrue();
    }

    @Test
    void convert_json_to_nested_object() {
        // Given
        String json =
                """
                {
                    "nested": {
                        "inner_key": "inner_value"
                    }
                }
                """;

        // When
        Document document = AwsDocumentConverter.documentFromJson(json);

        // Then
        Document nested = document.asMap().get("nested");
        assertThat(nested.asMap().get("inner_key").asString()).isEqualTo("inner_value");
    }

    @Test
    void convert_tool_specification_to_document() {
        // Given
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("test-tool")
                .description("Test tool description")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("param1")
                        .addIntegerProperty("param2")
                        .build())
                .build();

        // When
        Document document = AwsDocumentConverter.convertJsonObjectSchemaToDocument(toolSpec);

        // Then
        Map<String, Document> docMap = document.asMap();
        assertThat(docMap.get("type").asString()).isEqualTo("object");
        assertThat(docMap.get("description").asString()).isEqualTo("Test tool description");

        Document properties = docMap.get("properties");
        assertThat(properties.asMap()).containsKey("param1");
        assertThat(properties.asMap()).containsKey("param2");

        List<Document> required = docMap.get("required").asList();
        assertThat(required).containsAll(List.of(Document.fromString("param1"), Document.fromString("param2")));
    }

    @Test
    void handle_empty_document() {
        // Given
        Document emptyDoc = Document.fromMap(new HashMap<>());

        // When
        String json = AwsDocumentConverter.documentToJson(emptyDoc);

        // Then
        assertThat(json).isEqualToIgnoringWhitespace("{}");
    }

    @Test
    void handle_invalid_json() {
        // Given
        String invalidJson = "invalid json";

        // When/Then
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> AwsDocumentConverter.documentFromJson(invalidJson));
    }
}
