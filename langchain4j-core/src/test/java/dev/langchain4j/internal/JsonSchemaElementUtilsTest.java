package dev.langchain4j.internal;

import static dev.langchain4j.internal.JsonSchemaElementUtils.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import dev.langchain4j.model.output.structured.Description;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JsonSchemaElementUtilsTest {

    static class CustomClass {}

    @Test
    void is_custom_class() {

        assertThat(JsonSchemaElementUtils.isCustomClass(CustomClass.class)).isTrue();

        assertThat(JsonSchemaElementUtils.isCustomClass(Integer.class)).isFalse();
        assertThat(JsonSchemaElementUtils.isCustomClass(LocalDateTime.class)).isFalse();
    }

    static class Order {

        Address billingAddress;
        Address shippingAddress;
    }

    static class Address {

        String city;
    }

    @Test
    void should_not_use_reference_schema_when_no_recursion() {

        // given
        Class<Order> clazz = Order.class;

        // when
        JsonSchemaElement jsonSchemaElement = jsonSchemaElementFrom(clazz, null, null, true, new LinkedHashMap<>());

        // then
        assertThat(jsonSchemaElement)
                .isEqualTo(JsonObjectSchema.builder()
                        .addProperty(
                                "billingAddress",
                                JsonObjectSchema.builder()
                                        .addStringProperty("city")
                                        .required("city")
                                        .build())
                        .addProperty(
                                "shippingAddress",
                                JsonObjectSchema.builder()
                                        .addStringProperty("city")
                                        .required("city")
                                        .build())
                        .required("billingAddress", "shippingAddress")
                        .build());
    }

    @Test
    void should_set_default_description_for_uuid() {

        // given
        Class<UUID> clazz = UUID.class;

        // when
        JsonSchemaElement jsonSchemaElement = jsonSchemaElementFrom(clazz, null, null, true, new LinkedHashMap<>());

        // then
        assertThat(jsonSchemaElement)
                .isEqualTo(JsonStringSchema.builder()
                        .description("String in a UUID format")
                        .build());
    }

    static class MyClassWithUuid {

        UUID uuid;
    }

    @Test
    void should_set_default_description_for_uuid_in_class() {

        // given
        Class<MyClassWithUuid> clazz = MyClassWithUuid.class;

        // when
        JsonSchemaElement jsonSchemaElement = jsonSchemaElementFrom(clazz, null, null, true, new LinkedHashMap<>());

        // then
        assertThat(jsonSchemaElement)
                .isEqualTo(JsonObjectSchema.builder()
                        .addStringProperty("uuid", "String in a UUID format")
                        .required("uuid")
                        .build());
    }

    static class MyClassWithDescribedUuid {

        @Description("My UUID")
        UUID uuid;
    }

    @Test
    void should_use_non_null_description_for_uuid() {

        // given
        Class<MyClassWithDescribedUuid> clazz = MyClassWithDescribedUuid.class;

        // when
        JsonSchemaElement jsonSchemaElement = jsonSchemaElementFrom(clazz, null, null, true, new LinkedHashMap<>());

        // then
        assertThat(jsonSchemaElement)
                .isEqualTo(JsonObjectSchema.builder()
                        .addStringProperty("uuid", "My UUID")
                        .required("uuid")
                        .build());
    }

    @Test
    void givenVisitedJsonObjectSchema_whenDescriptionIsDifferent_thenReturnsNewSchemaWithUpdatedDescription() {
        JsonObjectSchema jsonObjectSchema = JsonObjectSchema.builder()
                .description("old")
                .addStringProperty("a")
                .build();
        Map<Class<?>, VisitedClassMetadata> visited =
                Map.of(CustomClass.class, new VisitedClassMetadata(jsonObjectSchema, "ref-object", false));

        JsonSchemaElement result = jsonObjectOrReferenceSchemaFrom(CustomClass.class, "new", false, visited, false);

        assertThat(result).isNotSameAs(jsonObjectSchema);
        assertThat(result.description()).isEqualTo("new");
    }

    @Test
    void givenVisitedJsonObjectSchema_whenDescriptionIsSame_thenReturnsSameInstance() {
        JsonObjectSchema jsonObjectSchema = JsonObjectSchema.builder()
                .description("same-desc")
                .addStringProperty("a")
                .build();
        Map<Class<?>, VisitedClassMetadata> visited =
                Map.of(CustomClass.class, new VisitedClassMetadata(jsonObjectSchema, "ref-object", false));

        JsonSchemaElement result =
                jsonObjectOrReferenceSchemaFrom(CustomClass.class, "same-desc", false, visited, false);

        assertThat(result).isSameAs(jsonObjectSchema);
        assertThat(result.description()).isEqualTo("same-desc");
    }

    @Test
    void toMap_not_strict() throws JsonProcessingException {

        // given
        JsonSchemaElement person = JsonObjectSchema.builder()
                .addStringProperty("name")
                .addStringProperty("age")
                .required("name")
                .build();

        // when
        Map<String, Object> map = toMap(person, false);

        // then
        assertThat(new ObjectMapper().writeValueAsString(map))
                .isEqualToIgnoringWhitespace(
                        """
                {
                   "type":"object",
                   "properties":{
                      "name":{
                         "type":"string"
                      },
                      "age":{
                         "type":"string"
                      }
                   },
                   "required":[
                      "name"
                   ]
                }
                """);
    }

    @Test
    void toMap_strict() throws JsonProcessingException {

        // given
        JsonSchemaElement person = JsonObjectSchema.builder()
                .addStringProperty("name")
                .addStringProperty("age")
                .required("name")
                .build();

        // when
        Map<String, Object> map = toMap(person, true);

        // then
        assertThat(new ObjectMapper().writeValueAsString(map))
                .isEqualToIgnoringWhitespace(
                        """
                {
                   "type":"object",
                   "properties":{
                      "name":{
                         "type":"string"
                      },
                      "age":{
                         "type":["string", "null"]
                      }
                   },
                   "required":[
                      "name", "age"
                   ],
                   "additionalProperties": false
                }
                """);
    }

    @Test
    void nativeSchemaToMap() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        String rawJsonSchema =
                """
        {
            "additionalProperties": false,
            "type" : "object",
            "properties" : {
              "$schema" : {
                "type" : "string"
              },
              "name" : {
                "type" : "string"
              }
            },
            "required": [ "name" ]
        }
        """;
        var nativeJson = JsonRawSchema.from(rawJsonSchema);
        var map = toMap(nativeJson);
        assertThat(mapper.writeValueAsString(map))
                .as("injection of existing full-blown schemas as string are possible")
                .isEqualToIgnoringWhitespace(rawJsonSchema);
    }

    @Test
    void stringIsJsonCompatible() {
        assertThat(isJsonString(char.class)).isTrue();
        assertThat(isJsonString(String.class)).isTrue();
        assertThat(isJsonString(Character.class)).isTrue();
        assertThat(isJsonString(StringBuffer.class)).isTrue();
        assertThat(isJsonString(StringBuilder.class)).isTrue();
        assertThat(isJsonString(CharSequence.class)).isTrue();
        assertThat(isJsonString(UUID.class)).isTrue();
    }

    @Test
    void collectionIsJsonCompatible() {
        assertThat(isJsonArray(String[].class)).isTrue();
        assertThat(isJsonArray(Integer[].class)).isTrue();
        assertThat(isJsonArray(int[].class)).isTrue();

        assertThat(isJsonArray(List.class)).isTrue();
        assertThat(isJsonArray(Set.class)).isTrue();
        assertThat(isJsonArray(Deque.class)).isTrue();
        assertThat(isJsonArray(Collection.class)).isTrue();
        assertThat(isJsonArray(Iterable.class)).isTrue();
    }

    @Test
    void should_create_schema_for_enum() {

        // given
        enum MyEnum {
            A, B, C;
        }

        // when
        JsonSchemaElement schema = jsonSchemaElementFrom(MyEnum.class);

        // then
        assertThat(schema).isEqualTo(JsonEnumSchema.builder()
                .enumValues("A", "B", "C")
                .build());
    }

    @Test
    void should_create_schema_for_enum_with_custom_toString() {

        // given
        enum MyEnumWithToString {
            A, B, C;

            @Override
            public String toString() {
                return "[" + name() + "]";
            }
        }

        assertThat(MyEnumWithToString.A.toString()).isEqualTo("[A]");

        // when
        JsonSchemaElement schema = jsonSchemaElementFrom(MyEnumWithToString.class);

        // then
        assertThat(schema).isEqualTo(JsonEnumSchema.builder()
                .enumValues("A", "B", "C")
                .build());
    }

    @Test
    void shouldConvertJsonStringSchemaToMap() {
        JsonStringSchema schema = JsonStringSchema.builder()
                .description("string description")
                .build();

        Map<String, Object> map = JsonSchemaElementUtils.toMap(schema);

        assertThat(map)
                .containsEntry("type", "string")
                .containsEntry("description", "string description");
    }

    @Test
    void shouldConvertJsonIntegerSchemaToMap() {
        JsonIntegerSchema schema = JsonIntegerSchema.builder()
                .description("integer description")
                .build();

        Map<String, Object> map = JsonSchemaElementUtils.toMap(schema);

        assertThat(map)
                .containsEntry("type", "integer")
                .containsEntry("description", "integer description");
    }

    @Test
    void shouldConvertJsonNumberSchemaToMap() {
        JsonNumberSchema schema = JsonNumberSchema.builder()
                .description("number description")
                .build();

        Map<String, Object> map = JsonSchemaElementUtils.toMap(schema);

        assertThat(map)
                .containsEntry("type", "number")
                .containsEntry("description", "number description");
    }

    @Test
    void shouldConvertJsonBooleanSchemaToMap() {
        JsonBooleanSchema schema = JsonBooleanSchema.builder()
                .description("boolean description")
                .build();

        Map<String, Object> map = JsonSchemaElementUtils.toMap(schema);

        assertThat(map)
                .containsEntry("type", "boolean")
                .containsEntry("description", "boolean description");
    }

    @Test
    void shouldConvertJsonEnumSchemaToMap() {
        JsonEnumSchema schema = JsonEnumSchema.builder()
                .enumValues(Arrays.asList("A", "B", "C"))
                .description("enum description")
                .build();

        Map<String, Object> map = JsonSchemaElementUtils.toMap(schema);

        assertThat(map)
                .containsEntry("type", "string")
                .containsEntry("description", "enum description")
                .containsEntry("enum", Arrays.asList("A", "B", "C"));
    }

    @Test
    void shouldConvertJsonArraySchemaToMap() {
        JsonArraySchema schema = JsonArraySchema.builder()
                .items(JsonSchemaElementUtils.jsonSchemaElementFrom(String.class))
                .description("array description")
                .build();

        Map<String, Object> map = JsonSchemaElementUtils.toMap(schema);

        assertThat(map)
                .containsEntry("type", "array")
                .containsEntry("description", "array description")
                .containsKey("items");

        assertThat((Map<String, Object>) map.get("items")).containsEntry("type", "string");
    }

    @Test
    void shouldConvertJsonObjectSchemaToMap() {
        class Sample {
            String name;
            int age;
        }

        JsonObjectSchema schema = (JsonObjectSchema) JsonSchemaElementUtils.jsonSchemaElementFrom(Sample.class);

        Map<String, Object> map = JsonSchemaElementUtils.toMap(schema);

        assertThat(map).containsEntry("type", "object");
        assertThat(map).containsKey("properties");

        Map<String, Map<String, Object>> properties = (Map<String, Map<String, Object>>) map.get("properties");
        assertThat(properties).containsKeys("name", "age");
        assertThat(properties.get("name")).containsEntry("type", "string");
        assertThat(properties.get("age")).containsEntry("type", "integer");
    }

    @Test
    void shouldConvertJsonReferenceSchemaToMap() {
        JsonReferenceSchema schema = JsonReferenceSchema.builder()
                .reference("my-ref")
                .build();

        Map<String, Object> map = JsonSchemaElementUtils.toMap(schema);

        assertThat(map).containsEntry("$ref", "#/$defs/my-ref");
    }

    @Test
    void shouldConvertJsonAnyOfSchemaToMap() {
        JsonAnyOfSchema schema = JsonAnyOfSchema.builder()
                .anyOf(Arrays.asList(
                        JsonSchemaElementUtils.jsonSchemaElementFrom(String.class),
                        JsonSchemaElementUtils.jsonSchemaElementFrom(Integer.class)
                ))
                .description("anyOf description")
                .build();

        Map<String, Object> map = JsonSchemaElementUtils.toMap(schema);

        assertThat(map).containsEntry("description", "anyOf description");
        assertThat(map).containsKey("anyOf");

        List<Map<String, Object>> anyOf = (List<Map<String, Object>>) map.get("anyOf");
        assertThat(anyOf).hasSize(2);
        assertThat(anyOf.get(0)).containsEntry("type", "string");
        assertThat(anyOf.get(1)).containsEntry("type", "integer");
    }

    @Test
    void shouldConvertJsonNullSchemaToMap() {
        JsonNullSchema schema = new JsonNullSchema();

        Map<String, Object> map = JsonSchemaElementUtils.toMap(schema);

        assertThat(map).containsEntry("type", "null");
    }

    @Test
    void shouldConvertJsonRawSchemaToMap() {
        Map<String, Object> rawMap = Map.of("foo", "bar");
        JsonRawSchema schema = JsonRawSchema.builder().schema(Json.toJson(rawMap)).build();

        Map<String, Object> map = JsonSchemaElementUtils.toMap(schema);

        assertThat(map).containsEntry("foo", "bar");
    }

    // --- Inherited fields tests ---

    static class ParentClass {
        String parentField;
        int parentAge;
    }

    static class ChildClass extends ParentClass {
        String childField;
    }

    @Test
    void should_not_include_inherited_fields_by_default() {
        // when
        JsonSchemaElement schema = jsonSchemaElementFrom(ChildClass.class);

        // then
        assertThat(schema).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema objectSchema = (JsonObjectSchema) schema;
        assertThat(objectSchema.properties()).containsKey("childField");
        assertThat(objectSchema.properties()).doesNotContainKey("parentField");
        assertThat(objectSchema.properties()).doesNotContainKey("parentAge");
    }

    @Test
    void should_include_inherited_fields_when_enabled() {
        // when
        JsonSchemaElement schema = jsonSchemaElementFrom(ChildClass.class, true);

        // then
        assertThat(schema).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema objectSchema = (JsonObjectSchema) schema;
        assertThat(objectSchema.properties()).containsKey("childField");
        assertThat(objectSchema.properties()).containsKey("parentField");
        assertThat(objectSchema.properties()).containsKey("parentAge");
    }

    static class GrandparentClass {
        String grandparentField;
    }

    static class MiddleClass extends GrandparentClass {
        String middleField;
    }

    static class GrandchildClass extends MiddleClass {
        String grandchildField;
    }

    @Test
    void should_include_fields_from_entire_hierarchy() {
        // when
        JsonSchemaElement schema = jsonSchemaElementFrom(GrandchildClass.class, true);

        // then
        assertThat(schema).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema objectSchema = (JsonObjectSchema) schema;
        assertThat(objectSchema.properties()).containsKey("grandchildField");
        assertThat(objectSchema.properties()).containsKey("middleField");
        assertThat(objectSchema.properties()).containsKey("grandparentField");
    }

    static class ParentWithShadowed {
        String name;
    }

    static class ChildWithShadowed extends ParentWithShadowed {
        String name; // shadows parent field
        String extra;
    }

    @Test
    void child_field_should_take_precedence_over_parent_shadowed_field() {
        // when
        List<java.lang.reflect.Field> fields = collectFields(ChildWithShadowed.class, true);

        // then - child's "name" should be present, not parent's
        assertThat(fields).hasSize(2);
        assertThat(fields.stream().map(java.lang.reflect.Field::getName)).containsExactly("name", "extra");
        assertThat(fields.get(0).getDeclaringClass()).isEqualTo(ChildWithShadowed.class);
    }

    // --- Parent-first ordering test ---

    @Test
    void should_order_inherited_fields_parent_first() {
        // when
        JsonSchemaElement schema = jsonSchemaElementFrom(GrandchildClass.class, true);

        // then - parent fields should appear before child fields
        assertThat(schema).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema objectSchema = (JsonObjectSchema) schema;
        List<String> fieldNames = new ArrayList<>(objectSchema.properties().keySet());
        assertThat(fieldNames).containsExactly("grandparentField", "middleField", "grandchildField");
    }
}
