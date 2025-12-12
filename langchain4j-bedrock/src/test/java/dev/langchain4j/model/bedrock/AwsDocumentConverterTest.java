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
                        .addIntegerProperty("param3")
                        .addStringProperty("param4")
                        .required( "param2", "param4" )
                        .build())
                .build();

        // When
        Document document = AwsDocumentConverter.convertJsonObjectSchemaToDocument(toolSpec);

        // Then
        Map<String, Document> docMap = document.asMap();
        assertThat(docMap.get("type").asString()).isEqualTo("object");
        assertThat(docMap.get("description").asString()).isEqualTo("Test tool description");

        Document properties = docMap.get("properties");
        assertThat(properties.asMap().keySet()).containsExactlyInAnyOrder("param1","param2","param3","param4");
 
        List<Document> required = docMap.get("required").asList();
        assertThat(required).containsExactlyInAnyOrder(Document.fromString("param2"),Document.fromString("param4"));
    }

    @Test
    void convert_json_objec_schema_to_document_withNoExplicitRequiredFields(){
        // Given - properties defined but none marked as required
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name( "test-tool" )
                .description( "Tool with no required fields" )
                .parameters( JsonObjectSchema.builder()
                        .addStringProperty( "param1" )
                        .addIntegerProperty( "param2" )
                        .addStringProperty( "param3" )
                        .build() )
                .build();

        // When
        Document document = AwsDocumentConverter.convertJsonObjectSchemaToDocument( toolSpec );

        // Then - required list should be empty
        Map<String, Document> docMap = document.asMap();
        Document properties = docMap.get( "properties" );
        assertThat( properties.asMap().keySet() )
                .containsExactlyInAnyOrder( "param1", "param2", "param3" );

        List<Document> required = docMap.get( "required" ).asList();
        assertThat( required ).isEmpty();
    }

    @Test
    void convert_json_objec_schema_to_document_withAllPropertiesRequired(){
        // Given - all properties explicitly marked as required
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name( "all-required-tool" )
                .parameters( JsonObjectSchema.builder()
                        .addStringProperty( "param1" )
                        .addIntegerProperty( "param2" )
                        .addStringProperty( "param3" )
                        .required( "param1", "param2", "param3" )
                        .build() )
                .build();

        // When
        Document document = AwsDocumentConverter.convertJsonObjectSchemaToDocument( toolSpec );

        // Then - all should be in required list
        Map<String, Document> docMap = document.asMap();
        List<Document> required = docMap.get( "required" ).asList();
        assertThat( required )
                .hasSize( 3 )
                .containsExactlyInAnyOrder(
                        Document.fromString( "param1" ),
                        Document.fromString( "param2" ),
                        Document.fromString( "param3" )
                );
    }

    @Test
    void convert_json_objec_schema_to_document_withSingleRequiredParameter(){
        // Given - only one parameter marked as required
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name( "single-required-tool" )
                .parameters( JsonObjectSchema.builder()
                        .addStringProperty( "optional1" )
                        .addIntegerProperty( "required1" )
                        .addStringProperty( "optional2" )
                        .required( "required1" )
                        .build() )
                .build();

        // When
        Document document = AwsDocumentConverter.convertJsonObjectSchemaToDocument( toolSpec );

        // Then - only required1 should be in required list
        Map<String, Document> docMap = document.asMap();
        Document properties = docMap.get( "properties" );
        assertThat( properties.asMap().keySet() ).hasSize( 3 );

        List<Document> required = docMap.get( "required" ).asList();
        assertThat( required )
                .hasSize( 1 )
                .containsExactly( Document.fromString( "required1" ) );
    }

    @Test
    void convert_json_objec_schema_to_document_withNullParameters(){
        // Given - null parameters (negative case)
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name( "no-params-tool" )
                .description( "Tool with no parameters" )
                .parameters( null )
                .build();

        // When
        Document document = AwsDocumentConverter.convertJsonObjectSchemaToDocument( toolSpec );

        // Then - should not have properties or required fields
        Map<String, Document> docMap = document.asMap();
        assertThat( docMap.get( "type" ).asString() ).isEqualTo( "object" );
        assertThat( docMap.get( "description" ).asString() ).isEqualTo( "Tool with no parameters" );
        assertThat( docMap.containsKey( "properties" ) ).isFalse();
        assertThat( docMap.containsKey( "required" ) ).isFalse();
    }

    @Test
    void convert_json_objec_schema_to_document_withEmptyProperties(){
        // Given - empty properties (edge case)
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name( "empty-props-tool" )
                .description( "Tool with empty properties" )
                .parameters( JsonObjectSchema.builder().build() )
                .build();

        // When
        Document document = AwsDocumentConverter.convertJsonObjectSchemaToDocument( toolSpec );

        // Then - both properties and required should be empty
        Map<String, Document> docMap = document.asMap();
        Document properties = docMap.get( "properties" );
        assertThat( properties.asMap() ).isEmpty();

        List<Document> required = docMap.get( "required" ).asList();
        assertThat( required ).isEmpty();
    }

    @Test
    void convert_json_objec_schema_to_document_withComplexPropertyTypes(){
        // Given - various property types with only some required
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name( "complex-tool" )
                .parameters( JsonObjectSchema.builder()
                        .addStringProperty( "stringProp" )
                        .addIntegerProperty( "intProp" )
                        .addBooleanProperty( "boolProp" )
                        .addNumberProperty( "numberProp" )
                        .addEnumProperty( "arrayProp", List.of( "enumA", "enumB" ) )
                        .required( "intProp", "arrayProp" ) // Only 2 of 5 required
                        .build() )
                .build();

        // When
        Document document = AwsDocumentConverter.convertJsonObjectSchemaToDocument( toolSpec );

        // Then - only intProp and arrayProp should be required
        Map<String, Document> docMap = document.asMap();
        Document properties = docMap.get( "properties" );
        assertThat( properties.asMap().keySet() )
                .containsExactlyInAnyOrder( "stringProp", "intProp", "boolProp", "numberProp", "arrayProp" );

        List<Document> required = docMap.get( "required" ).asList();
        assertThat( required )
                .hasSize( 2 )
                .containsExactlyInAnyOrder(
                        Document.fromString( "intProp" ),
                        Document.fromString( "arrayProp" )
                );
    }

    @Test
    void convert_json_objec_schema_to_document_withNullDescription(){
        // Given - null description
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name( "no-desc-tool" )
                .parameters( JsonObjectSchema.builder()
                        .addStringProperty( "param1" )
                        .required( "param1" )
                        .build() )
                .build();

        // When
        Document document = AwsDocumentConverter.convertJsonObjectSchemaToDocument( toolSpec );

        // Then - should work without description
        Map<String, Document> docMap = document.asMap();
        assertThat( docMap.containsKey( "description" ) ).isFalse();
        assertThat( docMap.get( "type" ).asString() ).isEqualTo( "object" );

        List<Document> required = docMap.get( "required" ).asList();
        assertThat( required ).containsExactly( Document.fromString( "param1" ) );
    }

    @Test
    void convert_json_objec_schema_to_document_requiredFieldNotInProperties(){
        // Given - This shouldn't happen in practice, but tests defensive coding
        // if schema mistakenly marks a non-existent property as required
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name( "invalid-tool" )
                .parameters( JsonObjectSchema.builder()
                        .addStringProperty( "param1" )
                        .addIntegerProperty( "param2" )
                        .required( "param1", "param2", "nonexistent" ) // nonexistent not in properties
                        .build() )
                .build();

        // When
        Document document = AwsDocumentConverter.convertJsonObjectSchemaToDocument( toolSpec );

        // Then - should include all items from required() call
        Map<String, Document> docMap = document.asMap();
        List<Document> required = docMap.get( "required" ).asList();
        assertThat( required )
                .containsExactlyInAnyOrder(
                        Document.fromString( "param1" ),
                        Document.fromString( "param2" ),
                        Document.fromString( "nonexistent" )
                );
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

    @Test
    void handle_null() {

        // given
        Document document = null;

        // when
        String json = AwsDocumentConverter.documentToJson(document);

        // then
        assertThat(json).isEqualTo("{}");
    }
}
