package dev.langchain4j.internal;

import static dev.langchain4j.internal.JsonSchemaElementUtils.isJsonArray;
import static dev.langchain4j.internal.JsonSchemaElementUtils.isJsonString;
import static dev.langchain4j.internal.JsonSchemaElementUtils.jsonSchemaElementFrom;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.output.structured.Description;
import java.time.LocalDateTime;
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
        assertThat(new ObjectMapper().writeValueAsString(map)).isEqualToIgnoringWhitespace("""
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
                """
        );
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
        assertThat(new ObjectMapper().writeValueAsString(map)).isEqualToIgnoringWhitespace("""
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
                """
        );
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
}
