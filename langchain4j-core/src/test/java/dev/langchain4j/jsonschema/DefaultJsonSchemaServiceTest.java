package dev.langchain4j.jsonschema;

import static dev.langchain4j.internal.Utils.mapOf;

import com.google.gson.Gson;
import dev.langchain4j.TestReflectUtil;

import dev.langchain4j.exception.JsonSchemaDeserializationException;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class DefaultJsonSchemaServiceTest implements WithAssertions  {
    public static final Gson GSON = new Gson();
    public static final JsonSchemaServiceFactories.Service DEFAULT_SERVICE =
            JsonSchemaServiceFactories.loadService();

    public enum ExampleEnum { A, B, C }

    private static Object deserialize(Object argument, Type type)
            throws JsonSchemaDeserializationException {
        return DEFAULT_SERVICE.deserialize(GSON.toJson(argument), type);
    }

    @Test
    public void test_defaultSanitizer_deserializeString(){
        // Pass-through unhandled types.
        assertThatCode(
                () -> assertThat(deserialize("abc", String.class))
                        .isEqualTo("abc"))
                .as("string input")
                .doesNotThrowAnyException();
    }

    @Test
    public void test_defaultSanitizer_deserializeEnum() {
        assertThatCode(
                () -> assertThat(deserialize("A", ExampleEnum.class))
                        .isEqualTo(ExampleEnum.A))
                .as("string input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(deserialize(ExampleEnum.A, ExampleEnum.class))
                        .isEqualTo(ExampleEnum.A))
                .as("enum input")
                .doesNotThrowAnyException();

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize("D", ExampleEnum.class))
                .withMessageContaining("has invalid enum value for");
    }

    @Test
    public void test_defaultSanitizer_deserializeBoolean() {
        assertThatCode(
                () -> assertThat(deserialize(true, boolean.class))
                        .isEqualTo(true))
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(deserialize(Boolean.FALSE, boolean.class))
                        .isEqualTo(false))
                .doesNotThrowAnyException();

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize("chars", boolean.class))
                .withMessageContaining(
                        "is not convertable to boolean, got java.lang.String: \"chars\"");
    }

    @Test
    public void test_defaultSanitizer_deserializeDouble() {
        assertThatCode(
                () -> assertThat(deserialize(1.5, double.class))
                        .isEqualTo(1.5))
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(deserialize(1.5, Double.class))
                        .isEqualTo(1.5))
                .doesNotThrowAnyException();

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize("abc", double.class))
                .withMessageContaining(
                        "is not convertable to double, got java.lang.String: \"abc\"");
    }

    @Test
    public void test_defaultSanitizer_deserializeFloat() {
        assertThatCode(
                () -> assertThat(deserialize(1.5, float.class))
                        .isEqualTo(1.5f))
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(deserialize(1.5, Float.class))
                        .isEqualTo(1.5f))
                .doesNotThrowAnyException();

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize(1.5 * ((double) Float.MAX_VALUE), float.class))
                .withMessageContaining("is out of range for float:");

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize(-1.5 * ((double) Float.MAX_VALUE), float.class))
                .withMessageContaining("is out of range for float:");
    }

    @Test
    public void test_defaultSanitizer_deserializeInt() {
        assertThatCode(
                () -> assertThat(deserialize(1.0, int.class))
                        .isEqualTo(1))
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(deserialize(Integer.MAX_VALUE, int.class))
                        .isEqualTo(Integer.MAX_VALUE))
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(deserialize(Integer.MIN_VALUE, Integer.class))
                        .isEqualTo(Integer.MIN_VALUE))
                .doesNotThrowAnyException();

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize(1.5, int.class))
                .withMessageContaining("has non-integer value");

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize(Integer.MAX_VALUE + 1.0, int.class))
                .withMessageContaining("is out of range for int:");

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize(Integer.MIN_VALUE - 1.0, Integer.class))
                .withMessageContaining("is out of range for java.lang.Integer:");
    }

    @Test
    public void test_defaultSanitizer_deserializeLong() {
        assertThatCode(
                () -> assertThat(deserialize(1.0, long.class))
                        .isEqualTo(1L))
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(deserialize(Long.MAX_VALUE, long.class))
                        .isEqualTo(Long.MAX_VALUE))
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(deserialize(Long.MIN_VALUE, Long.class))
                        .isEqualTo(Long.MIN_VALUE))
                .doesNotThrowAnyException();

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize(1.5, long.class))
                .withMessageContaining("has non-integer value");

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize(1.5 * ((double) Long.MAX_VALUE), long.class))
                .withMessageContaining("is out of range for long:");

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize(1.5 * ((double) Long.MIN_VALUE), Long.class))
                .withMessageContaining("is out of range for java.lang.Long:");
    }

    @Test
    public void test_defaultSanitizer_deserializeShort() {
        assertThatCode(
                () -> assertThat(deserialize(1.0, short.class))
                        .isEqualTo((short) 1))
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(deserialize(Short.MAX_VALUE, short.class))
                        .isEqualTo(Short.MAX_VALUE))
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(deserialize(Short.MIN_VALUE, Short.class))
                        .isEqualTo(Short.MIN_VALUE))
                .doesNotThrowAnyException();

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize(1.5, short.class))
                .withMessageContaining("has non-integer value");

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize(Short.MAX_VALUE + 1.0, short.class))
                .withMessageContaining("is out of range for short:");

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize(Short.MIN_VALUE - 1.0, Short.class))
                .withMessageContaining("is out of range for java.lang.Short:");
    }

    @Test
    public void test_defaultSanitizer_deserializeByte() {
        assertThatCode(
                () -> assertThat(deserialize(1.0, byte.class))
                        .isEqualTo((byte) 1))
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(deserialize(Byte.MAX_VALUE, byte.class))
                        .isEqualTo(Byte.MAX_VALUE))
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(deserialize(Byte.MIN_VALUE, Byte.class))
                        .isEqualTo(Byte.MIN_VALUE))
                .doesNotThrowAnyException();

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize(1.5, byte.class))
                .withMessageContaining("has non-integer value");

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize(Byte.MAX_VALUE + 1.0, byte.class))
                .withMessageContaining("is out of range for byte:");

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize(Byte.MIN_VALUE - 1.0, Byte.class))
                .withMessageContaining("is out of range for java.lang.Byte:");
    }

    @Test
    public void test_defaultSanitizer_deserializeBigDecimal() {
        assertThatCode(
                () -> assertThat(deserialize(1.5, BigDecimal.class))
                        .isEqualTo(BigDecimal.valueOf(1.5)))
                .doesNotThrowAnyException();

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize("abc", BigDecimal.class))
                .withMessageContaining(
                        "is not convertable to java.math.BigDecimal, got java.lang.String: \"abc\"");
    }

    @Test
    public void test_defaultSanitizer_deserializeBigInteger() {
        assertThatCode(
                () -> assertThat(deserialize(1, BigInteger.class))
                        .isEqualTo(BigInteger.valueOf(1)))
                .doesNotThrowAnyException();

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize(1.5, BigInteger.class))
                .withMessageContaining("has non-integer value");

        assertThatExceptionOfType(JsonSchemaDeserializationException.class)
                .isThrownBy(() -> deserialize("abc", BigInteger.class))
                .withMessageContaining(
                        "is not convertable to java.math.BigInteger, got java.lang.String: \"abc\"");
    }

    @Test
    public void test_defaultSanitizer_deserializeMap() throws JsonSchemaDeserializationException {
        // Test normal Map
        Map<String, Integer> normalMap = mapOf(entry("a", 1), entry("b", 2));
        Object deserializedNormalMap =
                deserialize(
                        normalMap,
                        new TestReflectUtil.TypeTrait<Map<String, Integer>>() {}.getType());
        assertThat(deserializedNormalMap).isEqualTo(normalMap);

        // Test HashMap
        Map<String, Object> hashMap = new HashMap<>(normalMap);
        Object deserialized =
                deserialize(
                        hashMap,
                        new TestReflectUtil.TypeTrait<HashMap<String, Integer>>() {}.getType());

        assertThat(deserialized).isEqualTo(hashMap).isInstanceOf(HashMap.class);
    }
}
