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
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    void should_fallback_to_raw_for_mixed_enum() {
        Map<String, Object> mixedEnum = Map.of("enum", List.of("ok", 42));
        JsonSchemaElement element = JsonSchemaElementJsonUtils.fromMap(mixedEnum);
        assertThat(element).isInstanceOf(JsonRawSchema.class);
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

    // ---- raw fallback for extra keywords ----

    @Test
    void should_fallback_to_raw_for_string_with_format() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "string");
        map.put("format", "date-time");
        assertRawFallback(map);
    }

    @Test
    void should_fallback_to_raw_for_string_with_pattern() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "string");
        map.put("pattern", "^[A-Z]+$");
        assertRawFallback(map);
    }

    @Test
    void should_fallback_to_raw_for_integer_with_minimum() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "integer");
        map.put("minimum", 0);
        assertRawFallback(map);
    }

    @Test
    void should_fallback_to_raw_for_numeric_enum() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enum", List.of(1, 2, 3));
        assertRawFallback(map);
    }

    @Test
    void should_fallback_to_raw_for_schema_valued_additionalProperties() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "object");
        map.put("additionalProperties", Map.of("type", "string"));
        assertRawFallback(map);
    }

    @Test
    void should_round_trip_nested_with_raw_fallback_child() {
        // outer object is typed, child with format falls back to raw
        Map<String, Object> childMap = new LinkedHashMap<>();
        childMap.put("type", "string");
        childMap.put("format", "date-time");

        Map<String, Object> propsMap = new LinkedHashMap<>();
        propsMap.put("name", Map.of("type", "string"));
        propsMap.put("timestamp", childMap);

        Map<String, Object> objectMap = new LinkedHashMap<>();
        objectMap.put("type", "object");
        objectMap.put("properties", propsMap);
        objectMap.put("required", List.of("name"));

        JsonSchemaElement element = JsonSchemaElementJsonUtils.fromMap(objectMap);
        assertThat(element).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema obj = (JsonObjectSchema) element;
        assertThat(obj.properties().get("name")).isInstanceOf(JsonStringSchema.class);
        assertThat(obj.properties().get("timestamp")).isInstanceOf(JsonRawSchema.class);

        // round-trip should preserve structure
        Map<String, Object> restored = JsonSchemaElementJsonUtils.toMap(element);
        assertThat(restored).isEqualTo(objectMap);
    }

    @Test
    void should_round_trip_array_with_null_items() {
        JsonArraySchema schema = JsonArraySchema.builder()
                .description("tags")
                .build();
        assertRoundTrip(schema);
    }

    // ---- description type safety ----

    @Test
    void should_reject_non_string_description_in_object() {
        Map<String, Object> map = Map.of("type", "object", "description", 123);
        assertThatThrownBy(() -> JsonSchemaElementJsonUtils.fromMap(map))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    @Test
    void should_reject_non_string_description_in_array() {
        Map<String, Object> map = Map.of("type", "array", "description", true);
        assertThatThrownBy(() -> JsonSchemaElementJsonUtils.fromMap(map))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    @Test
    void should_reject_non_string_description_in_anyof() {
        Map<String, Object> map = Map.of(
                "anyOf", List.of(Map.of("type", "string")),
                "description", 42);
        assertThatThrownBy(() -> JsonSchemaElementJsonUtils.fromMap(map))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    @Test
    void should_reject_non_string_description_in_string() {
        Map<String, Object> map = Map.of("type", "string", "description", 99);
        assertThatThrownBy(() -> JsonSchemaElementJsonUtils.fromMap(map))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    @Test
    void should_reject_non_string_description_in_enum() {
        Map<String, Object> map = Map.of("enum", List.of("A", "B"), "description", List.of("bad"));
        assertThatThrownBy(() -> JsonSchemaElementJsonUtils.fromMap(map))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    // ---- enum type validation ----

    @Test
    void should_fallback_to_raw_for_enum_with_non_string_type() {
        // {"type":"integer","enum":["A"]} — type is not "string", must fall back to raw
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "integer");
        map.put("enum", List.of("A", "B"));
        assertRawFallback(map);
    }

    @Test
    void should_fallback_to_raw_for_enum_with_null_type() {
        // {"type":null,"enum":["A"]} — type key present but null, must fall back to raw
        Map<String, Object> map = new HashMap<>();
        map.put("type", null);
        map.put("enum", List.of("A"));
        JsonSchemaElement element = JsonSchemaElementJsonUtils.fromMap(map);
        assertThat(element).isInstanceOf(JsonRawSchema.class);
    }

    // ---- explicit null values (typed models can't preserve them) ----

    @Test
    void should_fallback_to_raw_for_string_with_null_description() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "string");
        map.put("description", null);
        JsonSchemaElement element = JsonSchemaElementJsonUtils.fromMap(map);
        assertThat(element).isInstanceOf(JsonRawSchema.class);
    }

    @Test
    void should_fallback_to_raw_for_object_with_null_properties() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "object");
        map.put("properties", null);
        JsonSchemaElement element = JsonSchemaElementJsonUtils.fromMap(map);
        assertThat(element).isInstanceOf(JsonRawSchema.class);
    }

    @Test
    void should_fallback_to_raw_for_array_with_null_items() {
        // Note: this tests {"type":"array","items":null} from JSON (explicit null key),
        // NOT JsonArraySchema with items=null (which is a valid typed model)
        Map<String, Object> map = new HashMap<>();
        map.put("type", "array");
        map.put("items", null);
        JsonSchemaElement element = JsonSchemaElementJsonUtils.fromMap(map);
        assertThat(element).isInstanceOf(JsonRawSchema.class);
    }

    @Test
    void should_round_trip_enum_with_string_type() {
        // {"type":"string","enum":["A","B"]} — explicit type:"string" is fine
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "string");
        map.put("enum", List.of("A", "B"));
        JsonSchemaElement element = JsonSchemaElementJsonUtils.fromMap(map);
        assertThat(element).isInstanceOf(JsonEnumSchema.class);
        Map<String, Object> restored = JsonSchemaElementJsonUtils.toMap(element);
        assertThat(restored).isEqualTo(map);
    }

    private void assertRawFallback(Map<String, Object> map) {
        JsonSchemaElement element = JsonSchemaElementJsonUtils.fromMap(map);
        assertThat(element).isInstanceOf(JsonRawSchema.class);
        // verify round-trip: toMap on the raw schema should produce the same map
        Map<String, Object> restored = JsonSchemaElementJsonUtils.toMap(element);
        assertThat(restored).isEqualTo(map);
    }

    private void assertRoundTrip(JsonSchemaElement original) {
        Map<String, Object> map = JsonSchemaElementJsonUtils.toMap(original);
        JsonSchemaElement restored = JsonSchemaElementJsonUtils.fromMap(map);
        assertThat(restored).isEqualTo(original);
    }
}
