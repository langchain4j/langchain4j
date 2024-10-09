package dev.langchain4j.model.chat.request.json;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

import static dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper.jsonSchemaElementFrom;
import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemaElementHelperTest {

    static class CustomClass {

    }

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
        assertThat(jsonSchemaElement).isEqualTo(JsonObjectSchema.builder()
                .addObjectProperty("billingAddress", o -> o.addStringProperty("city").required("city"))
                .addObjectProperty("shippingAddress", o -> o.addStringProperty("city").required("city"))
                .required("billingAddress", "shippingAddress")
                .build());
    }
}