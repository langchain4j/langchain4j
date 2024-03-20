package dev.langchain4j.agent.tool;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;

class JsonSchemaPropertyTest implements WithAssertions {
    @Test
    public void test_common() {
        assertThat(JsonSchemaProperty.STRING)
                .isEqualTo(JsonSchemaProperty.type("string"));
        assertThat(JsonSchemaProperty.INTEGER)
                .isEqualTo(JsonSchemaProperty.type("integer"));
        assertThat(JsonSchemaProperty.NUMBER)
                .isEqualTo(JsonSchemaProperty.type("number"));
        assertThat(JsonSchemaProperty.OBJECT)
                .isEqualTo(JsonSchemaProperty.type("object"));
        assertThat(JsonSchemaProperty.ARRAY)
                .isEqualTo(JsonSchemaProperty.type("array"));
        assertThat(JsonSchemaProperty.BOOLEAN)
                .isEqualTo(JsonSchemaProperty.type("boolean"));
        assertThat(JsonSchemaProperty.NULL)
                .isEqualTo(JsonSchemaProperty.type("null"));
    }

    @Test
    public void test_equals_hash() {
        JsonSchemaProperty prop1 = new JsonSchemaProperty("key", "value");
        JsonSchemaProperty prop2 = new JsonSchemaProperty("key", "value");

        JsonSchemaProperty prop3 = new JsonSchemaProperty("key", 12);
        JsonSchemaProperty prop4 = new JsonSchemaProperty("abc", "value");

        assertThat(prop1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(prop2)
                .hasSameHashCodeAs(prop2);

        assertThat(prop1)
                .isNotEqualTo(prop3)
                .doesNotHaveSameHashCodeAs(prop3)
                .isNotEqualTo(prop4)
                .doesNotHaveSameHashCodeAs(prop4);

        assertThat(prop3)
                .isNotEqualTo(prop4)
                .doesNotHaveSameHashCodeAs(prop4);

        {
            JsonSchemaProperty enum1 = JsonSchemaProperty.enums("value1", "value2");
            JsonSchemaProperty enum2 = JsonSchemaProperty.enums("value1", "value2");

            assertThat(enum1)
                    .isEqualTo(enum2)
                    .hasSameHashCodeAs(enum2);
        }
    }

    @Test
    public void test_toString() {
        JsonSchemaProperty prop = new JsonSchemaProperty("key", "value");
        assertThat(prop.toString())
                .isEqualTo("JsonSchemaProperty { key = \"key\", value = value }");

        assertThat(JsonSchemaProperty.enums("value1", "value2").toString())
                .isEqualTo("JsonSchemaProperty { key = \"enum\", value = [value1, value2] }");
    }

    @Test
    public void test_type() {
        JsonSchemaProperty prop = JsonSchemaProperty.type("string");
        assertThat(prop.key()).isEqualTo("type");
        assertThat(prop.value()).isEqualTo("string");
    }

    @Test
    public void test_from() {
        JsonSchemaProperty prop = JsonSchemaProperty.from("key", "value");
        assertThat(prop.key()).isEqualTo("key");
        assertThat(prop.value()).isEqualTo("value");
    }

    @Test
    public void test_property() {
        assertThat(JsonSchemaProperty.property("key", "value"))
                .isEqualTo(JsonSchemaProperty.from("key", "value"));
    }

    @Test
    public void test_description() {
        assertThat(JsonSchemaProperty.description("value"))
                .isEqualTo(JsonSchemaProperty.from("description", "value"));
    }

    public enum EnumTest {
        VALUE1, value2, Value3
    }

    @Test
    public void test_enums() {
        {
            JsonSchemaProperty prop = JsonSchemaProperty.enums("value1", "value2");
            assertThat(prop.key()).isEqualTo("enum");
            assertThat(Arrays.equals((String[]) prop.value(), new String[]{"value1", "value2"})).isTrue();
        }

        {
            JsonSchemaProperty prop = JsonSchemaProperty.enums(EnumTest.VALUE1, EnumTest.value2, EnumTest.Value3);
            assertThat(prop.key()).isEqualTo("enum");
            assertThat(prop.value()).isEqualTo(asList("VALUE1", "value2", "Value3"));
        }

        {
            JsonSchemaProperty prop = JsonSchemaProperty.enums(EnumTest.class);
            assertThat(prop.key()).isEqualTo("enum");
            assertThat(prop.value()).isEqualTo(asList("VALUE1", "value2", "Value3"));
        }

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> JsonSchemaProperty.enums(Object.class, Integer.class))
                .withMessageContaining("should be enum");

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> JsonSchemaProperty.enums(Object.class))
                .withMessageContaining("should be enum");
    }

    @Test
    public void test_items() {
        JsonSchemaProperty prop = JsonSchemaProperty.items(JsonSchemaProperty.STRING);
        assertThat(prop.key()).isEqualTo("items");
        assertThat(prop.value()).isEqualTo(singletonMap("type", "string"));
    }
}