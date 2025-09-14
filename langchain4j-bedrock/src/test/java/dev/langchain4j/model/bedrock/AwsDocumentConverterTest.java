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
        docMap.put("null", Document.fromNull());
        Document document = Document.fromMap(docMap);

        // When
        String json = AwsDocumentConverter.documentToJson(document);

        // Then
        assertThat(json)
                .containsIgnoringWhitespaces("\"string\": \"test\"")
                .containsIgnoringWhitespaces("\"integer\": 42")
                .containsIgnoringWhitespaces("\"double\": 42.5")
                .containsIgnoringWhitespaces("\"boolean\": true")
                .containsIgnoringWhitespaces("\"null\": null");
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
        assertThat(json)
                .containsIgnoringWhitespaces("\"list\": [")
                .containsIgnoringWhitespaces("\"item1\"")
                .contains("2")
                .contains("true");
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
        assertThat(json)
                .containsIgnoringWhitespaces("\"nested\": {")
                .containsIgnoringWhitespaces("\"inner_key\": \"inner_value\"");
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

    @Test
    void convert_empty_list_to_json() {
        // Given
        Map<String, Document> docMap = new HashMap<>();
        docMap.put("emptyList", Document.fromList(Arrays.asList()));
        Document document = Document.fromMap(docMap);

        // When
        String json = AwsDocumentConverter.documentToJson(document);

        // Then
        assertThat(json).containsIgnoringWhitespaces("\"emptyList\": []");
    }

    @Test
    void convert_complex_nested_structure_to_json() {
        // Given
        Map<String, Document> level2 = new HashMap<>();
        level2.put("level2_key", Document.fromString("level2_value"));
        level2.put("level2_number", Document.fromNumber(123));

        Map<String, Document> level1 = new HashMap<>();
        level1.put("level1_object", Document.fromMap(level2));
        level1.put(
                "level1_list",
                Document.fromList(Arrays.asList(
                        Document.fromString("listItem1"),
                        Document.fromMap(Map.of("nestedInList", Document.fromBoolean(false))))));

        Map<String, Document> rootMap = new HashMap<>();
        rootMap.put("complex", Document.fromMap(level1));
        Document document = Document.fromMap(rootMap);

        // When
        String json = AwsDocumentConverter.documentToJson(document);

        // Then
        assertThat(json)
                .containsIgnoringWhitespaces("\"complex\":")
                .containsIgnoringWhitespaces("\"level1_object\":")
                .containsIgnoringWhitespaces("\"level2_key\": \"level2_value\"")
                .containsIgnoringWhitespaces("\"level2_number\": 123")
                .containsIgnoringWhitespaces("\"level1_list\":")
                .containsIgnoringWhitespaces("\"listItem1\"")
                .containsIgnoringWhitespaces("\"nestedInList\": false");
    }

    @Test
    void convert_tool_specification_with_no_parameters() {
        // Given
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("simple-tool")
                .description("Simple tool with no parameters")
                .build();

        // When
        Document document = AwsDocumentConverter.convertJsonObjectSchemaToDocument(toolSpec);

        // Then
        Map<String, Document> docMap = document.asMap();
        assertThat(docMap.get("type").asString()).isEqualTo("object");
        assertThat(docMap.get("description").asString()).isEqualTo("Simple tool with no parameters");

        // Should handle null/empty parameters gracefully
        if (docMap.containsKey("properties")) {
            assertThat(docMap.get("properties").asMap()).isEmpty();
        }
        if (docMap.containsKey("required")) {
            assertThat(docMap.get("required").asList()).isEmpty();
        }
    }

    @Test
    void handle_large_numbers() {
        // Given
        Map<String, Document> docMap = new HashMap<>();
        docMap.put("longValue", Document.fromNumber(Long.MAX_VALUE));
        docMap.put("bigDecimal", Document.fromNumber(Double.MAX_VALUE));
        Document document = Document.fromMap(docMap);

        // When
        String json = AwsDocumentConverter.documentToJson(document);

        // Then
        assertThat(json)
                .containsIgnoringWhitespaces("\"longValue\": " + Long.MAX_VALUE)
                .containsIgnoringWhitespaces("\"bigDecimal\": " + Double.MAX_VALUE);
    }

    @Test
    void handle_deeply_nested_structure() {
        // Given
        Document deepestLevel = Document.fromMap(Map.of("value", Document.fromString("deep")));
        Document level4 = Document.fromMap(Map.of("level5", deepestLevel));
        Document level3 = Document.fromMap(Map.of("level4", level4));
        Document level2 = Document.fromMap(Map.of("level3", level3));
        Document level1 = Document.fromMap(Map.of("level2", level2));
        Document root = Document.fromMap(Map.of("level1", level1));

        // When
        String json = AwsDocumentConverter.documentToJson(root);

        // Then
        assertThat(json).containsIgnoringWhitespaces("\"value\": \"deep\"");

        // And converting back should work
        Document converted = AwsDocumentConverter.documentFromJson(json);
        String deepValue = converted
                .asMap()
                .get("level1")
                .asMap()
                .get("level2")
                .asMap()
                .get("level3")
                .asMap()
                .get("level4")
                .asMap()
                .get("level5")
                .asMap()
                .get("value")
                .asString();
        assertThat(deepValue).isEqualTo("deep");
    }
}
