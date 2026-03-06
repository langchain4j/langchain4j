package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonSchemaElementJsonUtilsTest {

    @Test
    void toMap_should_reject_null() {
        assertThatThrownBy(() -> JsonSchemaElementJsonUtils.toMap(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromMap_should_reject_null() {
        assertThatThrownBy(() -> JsonSchemaElementJsonUtils.fromMap(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_round_trip_scalar_types() {
        assertRoundTrip(JsonStringSchema.builder().description("a name").build());
        assertRoundTrip(new JsonStringSchema());
        assertRoundTrip(JsonIntegerSchema.builder().description("count").build());
        assertRoundTrip(new JsonIntegerSchema());
        assertRoundTrip(JsonNumberSchema.builder().description("price").build());
        assertRoundTrip(new JsonNumberSchema());
        assertRoundTrip(JsonBooleanSchema.builder().description("active").build());
        assertRoundTrip(new JsonBooleanSchema());
        assertRoundTrip(new JsonNullSchema());
    }

    @Test
    void should_round_trip_enum_schema() {
        JsonEnumSchema enumSchema = JsonEnumSchema.builder()
                .description("status")
                .enumValues("ACTIVE", "INACTIVE")
                .build();
        assertRoundTrip(enumSchema);
    }

    @Test
    void should_round_trip_array_schema() {
        JsonArraySchema arraySchema = JsonArraySchema.builder()
                .description("tags")
                .items(JsonStringSchema.builder().description("tag name").build())
                .build();
        assertRoundTrip(arraySchema);
    }

    @Test
    void should_round_trip_object_schema() {
        JsonObjectSchema objectSchema = JsonObjectSchema.builder()
                .description("a person")
                .addStringProperty("name", "full name")
                .addIntegerProperty("age", "years old")
                .addBooleanProperty("active")
                .required("name", "age")
                .additionalProperties(false)
                .build();
        assertRoundTrip(objectSchema);
    }

    @Test
    void should_round_trip_nested_object_schema() {
        JsonObjectSchema addressSchema = JsonObjectSchema.builder()
                .addStringProperty("city")
                .addStringProperty("zip")
                .required("city")
                .build();
        JsonObjectSchema personSchema = JsonObjectSchema.builder()
                .addProperty(
                        "name", JsonStringSchema.builder().description("name").build())
                .addProperty("address", addressSchema)
                .addProperty(
                        "scores",
                        JsonArraySchema.builder()
                                .items(JsonNumberSchema.builder().build())
                                .build())
                .required("name")
                .build();
        assertRoundTrip(personSchema);
    }

    @Test
    void should_round_trip_anyof_schema() {
        JsonAnyOfSchema anyOfSchema = JsonAnyOfSchema.builder()
                .description("string or number")
                .anyOf(
                        JsonStringSchema.builder().build(),
                        JsonNumberSchema.builder().build())
                .build();
        assertRoundTrip(anyOfSchema);
    }

    @Test
    void should_round_trip_reference_schema() {
        JsonReferenceSchema refSchema =
                JsonReferenceSchema.builder().reference("node-id").build();

        Map<String, Object> map = JsonSchemaElementJsonUtils.toMap(refSchema);
        assertThat(map).containsEntry("$ref", "#/$defs/node-id");

        JsonSchemaElement restored = JsonSchemaElementJsonUtils.fromMap(map);
        assertThat(restored).isEqualTo(refSchema);
    }

    @Test
    void should_fallback_to_raw_schema_for_external_ref() {
        Map<String, Object> externalRef = Map.of("$ref", "https://example.com/schema.json");
        JsonSchemaElement element = JsonSchemaElementJsonUtils.fromMap(externalRef);
        assertThat(element).isInstanceOf(JsonRawSchema.class);
    }

    @Test
    void should_round_trip_object_with_definitions() {
        JsonReferenceSchema refSchema =
                JsonReferenceSchema.builder().reference("node-id").build();
        JsonObjectSchema nodeSchema = JsonObjectSchema.builder()
                .addStringProperty("value")
                .addProperty("child", refSchema)
                .definitions(Map.of(
                        "node-id",
                        JsonObjectSchema.builder().addStringProperty("value").build()))
                .build();
        assertRoundTrip(nodeSchema);
    }

    @Test
    void should_convert_raw_schema_to_map() {
        JsonRawSchema raw = JsonRawSchema.from("{\"type\":\"string\",\"format\":\"date-time\"}");
        Map<String, Object> map = JsonSchemaElementJsonUtils.toMap(raw);
        assertThat(map).containsEntry("type", "string").containsEntry("format", "date-time");
    }

    @Test
    void should_fallback_to_raw_schema_for_unknown_type() {
        Map<String, Object> unknownMap = Map.of("type", "customType", "format", "special");
        JsonSchemaElement element = JsonSchemaElementJsonUtils.fromMap(unknownMap);
        assertThat(element).isInstanceOf(JsonRawSchema.class);
    }

    @Test
    void should_fallback_to_raw_schema_when_no_type() {
        Map<String, Object> noTypeMap = Map.of("oneOf", List.of(Map.of("type", "string")));
        JsonSchemaElement element = JsonSchemaElementJsonUtils.fromMap(noTypeMap);
        assertThat(element).isInstanceOf(JsonRawSchema.class);
    }

    @Test
    void should_reject_invalid_ref_type() {
        Map<String, Object> badRef = Map.of("$ref", 42);
        assertThatThrownBy(() -> JsonSchemaElementJsonUtils.fromMap(badRef))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("$ref");
    }

    @Test
    void should_reject_invalid_anyof_type() {
        Map<String, Object> badAnyOf = Map.of("anyOf", "not-a-list");
        assertThatThrownBy(() -> JsonSchemaElementJsonUtils.fromMap(badAnyOf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anyOf");
    }

    @Test
    void should_reject_invalid_enum_type() {
        Map<String, Object> badEnum = Map.of("enum", "not-a-list");
        assertThatThrownBy(() -> JsonSchemaElementJsonUtils.fromMap(badEnum))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enum");
    }

    @Test
    void should_fallback_to_raw_schema_when_type_is_array() {
        // JSON Schema allows type: ["string", "null"] — this should fallback to raw
        Map<String, Object> arrayType = Map.of("type", List.of("string", "null"));
        JsonSchemaElement element = JsonSchemaElementJsonUtils.fromMap(arrayType);
        assertThat(element).isInstanceOf(JsonRawSchema.class);
    }

    @Test
    void should_reject_non_map_properties() {
        Map<String, Object> badProps = Map.of("type", "object", "properties", "bad");
        assertThatThrownBy(() -> JsonSchemaElementJsonUtils.fromMap(badProps))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("properties");
    }

    @Test
    void should_reject_non_map_property_value() {
        Map<String, Object> badPropValue = Map.of("type", "object", "properties", Map.of("x", "bad"));
        assertThatThrownBy(() -> JsonSchemaElementJsonUtils.fromMap(badPropValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("x");
    }

    @Test
    void should_reject_non_map_items() {
        Map<String, Object> badItems = Map.of("type", "array", "items", "bad");
        assertThatThrownBy(() -> JsonSchemaElementJsonUtils.fromMap(badItems))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("items");
    }

    @Test
    void should_reject_non_map_anyof_element() {
        Map<String, Object> badAnyOfElement = Map.of("anyOf", List.of("bad"));
        assertThatThrownBy(() -> JsonSchemaElementJsonUtils.fromMap(badAnyOfElement))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anyOf");
    }

    @Test
    void should_reject_non_map_definition_value() {
        Map<String, Object> badDefValue = Map.of("type", "object", "$defs", Map.of("node", "bad"));
        assertThatThrownBy(() -> JsonSchemaElementJsonUtils.fromMap(badDefValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("node");
    }

    @Test
    void should_reject_non_string_enum_element() {
        Map<String, Object> badEnumElement = Map.of("enum", List.of("ok", 42));
        assertThatThrownBy(() -> JsonSchemaElementJsonUtils.fromMap(badEnumElement))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enum");
    }

    @Test
    void should_reject_non_string_required_element() {
        Map<String, Object> badRequired = Map.of("type", "object", "required", List.of("ok", 42));
        assertThatThrownBy(() -> JsonSchemaElementJsonUtils.fromMap(badRequired))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void should_reject_non_list_required() {
        Map<String, Object> badRequiredType = Map.of("type", "object", "required", "bad");
        assertThatThrownBy(() -> JsonSchemaElementJsonUtils.fromMap(badRequiredType))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void should_reject_non_map_defs() {
        Map<String, Object> badDefs = Map.of("type", "object", "$defs", "bad");
        assertThatThrownBy(() -> JsonSchemaElementJsonUtils.fromMap(badDefs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("$defs");
    }

    private void assertRoundTrip(JsonSchemaElement original) {
        Map<String, Object> map = JsonSchemaElementJsonUtils.toMap(original);
        JsonSchemaElement restored = JsonSchemaElementJsonUtils.fromMap(map);
        assertThat(restored).isEqualTo(original);
    }
}
