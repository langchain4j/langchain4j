package dev.langchain4j.model.chat.request.json;

import static dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper.jsonSchemaElementFrom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.output.structured.Description;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JsonSchemaElementHelperTest {

    static class CustomClass {}

    @Test
    void test_isCustomClass() {

        assertThat(JsonSchemaElementHelper.isCustomClass(CustomClass.class)).isTrue();

        assertThat(JsonSchemaElementHelper.isCustomClass(Integer.class)).isFalse();
        assertThat(JsonSchemaElementHelper.isCustomClass(LocalDateTime.class)).isFalse();
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
        JsonSchemaElement jsonSchemaElement = jsonSchemaElementFrom(clazz, null, null, new LinkedHashMap<>());

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
        JsonSchemaElement jsonSchemaElement = jsonSchemaElementFrom(clazz, null, null, new LinkedHashMap<>());

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
        JsonSchemaElement jsonSchemaElement = jsonSchemaElementFrom(clazz, null, null, new LinkedHashMap<>());

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
        JsonSchemaElement jsonSchemaElement = jsonSchemaElementFrom(clazz, null, null, new LinkedHashMap<>());

        // then
        assertThat(jsonSchemaElement)
                .isEqualTo(JsonObjectSchema.builder()
                        .addStringProperty("uuid", "My UUID")
                        .required("uuid")
                        .build());
    }
}
