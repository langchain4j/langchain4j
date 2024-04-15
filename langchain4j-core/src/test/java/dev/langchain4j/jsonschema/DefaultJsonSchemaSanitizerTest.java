package dev.langchain4j.jsonschema;

import static dev.langchain4j.internal.Utils.mapOf;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import dev.langchain4j.exception.JsonSchemaDeserializationException;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

public class DefaultJsonSchemaSanitizerTest implements WithAssertions {
    public static final Gson GSON = new Gson();

    public enum ExampleEnum { A, B, C }

    @Test
    public void test_defaultSanitizer_sanitize_string() {
        DefaultJsonSchemaSanitizer sanitizer = DefaultJsonSchemaSanitizer.builder().build();
        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree("string"), String.class))
                        .returns("string", JsonElement::getAsString))
                .as("string input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(""), String.class))
                        .returns("", JsonElement::getAsString))
                .as("empty string input")
                .doesNotThrowAnyException();

        assertThatCode(() -> sanitizer.sanitize(GSON.toJsonTree(42), String.class))
                .as("number input")
                .isInstanceOf(JsonSchemaDeserializationException.class)
                .hasMessageContaining("is not convertable to java.lang.String, got java.lang.Number: 42");

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(null, String.class))
                        .isEqualTo(JsonNull.INSTANCE))
                .as("null input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(null), String.class))
                        .isEqualTo(JsonNull.INSTANCE))
                .as("JsonNull input")
                .doesNotThrowAnyException();
    }

    @Test
    public void test_defaultSanitizer_sanitize_integer() {
        DefaultJsonSchemaSanitizer sanitizer = DefaultJsonSchemaSanitizer.builder().build();
        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(42), Integer.class))
                        .returns(42, JsonElement::getAsNumber))
                .as("integer input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> {
                    assertThat(sanitizer.sanitize(GSON.toJsonTree(0), Integer.class))
                            .isEqualTo(GSON.toJsonTree(0));
                    assertThat(sanitizer.sanitize(GSON.toJsonTree(Integer.MAX_VALUE), Integer.class))
                            .isEqualTo(GSON.toJsonTree(Integer.MAX_VALUE));
                    assertThat(sanitizer.sanitize(GSON.toJsonTree(Integer.MIN_VALUE), Integer.class))
                            .isEqualTo(GSON.toJsonTree(Integer.MIN_VALUE));
                })
                .as("zero/max/min integer input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree("42"), Integer.class))
                        .returns(42, JsonElement::getAsNumber))
                .as("string input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(null, Integer.class))
                        .isEqualTo(JsonNull.INSTANCE))
                .as("null input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(null), Integer.class))
                        .isEqualTo(JsonNull.INSTANCE))
                .as("JsonNull input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(100L), Integer.class))
                        .returns(100, JsonElement::getAsNumber))
                .as("in bound long input")
                        .doesNotThrowAnyException();

        assertThatCode(
                () -> sanitizer.sanitize(GSON.toJsonTree(Long.MAX_VALUE - 42), Integer.class))
                .as("out of bound long input")
                .isInstanceOf(JsonSchemaDeserializationException.class)
                .hasMessageContaining("is out of range for java.lang.Integer");

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(100.0), Integer.class))
                        .returns(100, JsonElement::getAsNumber))
                .as("in bound double without fractional part input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> sanitizer.sanitize(GSON.toJsonTree(100.5), Integer.class))
                .as("in bound double with fractional part input")
                .isInstanceOf(JsonSchemaDeserializationException.class)
                .hasMessageContaining("has non-integer value for class java.lang.Integer");

        assertThatCode(
                () -> sanitizer.sanitize(GSON.toJsonTree(new ArrayList<>()), Integer.class))
                .as("empty list input")
                .isInstanceOf(JsonSchemaDeserializationException.class)
                .hasMessageContaining("is not convertable to java.lang.Integer, got java.util.List: []");
    }

    @Test
    public void test_defaultSanitizer_sanitize_float() {
        DefaultJsonSchemaSanitizer sanitizer = DefaultJsonSchemaSanitizer.builder().build();
        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(42.0F), Float.class))
                        .returns(42.0F, JsonElement::getAsFloat))
                .as("float input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> {
                    assertThat(sanitizer.sanitize(GSON.toJsonTree(0.0F), Float.class))
                            .isEqualTo(GSON.toJsonTree(0.0F));
                    assertThat(sanitizer.sanitize(GSON.toJsonTree(Float.MAX_VALUE), Float.class))
                            .isEqualTo(GSON.toJsonTree(Float.MAX_VALUE));
                    assertThat(sanitizer.sanitize(GSON.toJsonTree(Float.MIN_VALUE), Float.class))
                            .isEqualTo(GSON.toJsonTree(Float.MIN_VALUE));
                })
                .as("zero/max/min float input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree("42.0"), Float.class))
                        .returns(42.0F, JsonElement::getAsFloat))
                .as("string input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(null, Float.class))
                        .isEqualTo(JsonNull.INSTANCE))
                .as("null input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(null), Float.class))
                        .isEqualTo(JsonNull.INSTANCE))
                .as("JsonNull input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(100), Float.class))
                        .returns(100.0F, JsonElement::getAsFloat))
                .as("integer input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> sanitizer.sanitize(GSON.toJsonTree(new BigInteger("34028235000000000000000000000000000000000")), Float.class))
                .as("out of bound big integer input")
                .isInstanceOf(JsonSchemaDeserializationException.class)
                .hasMessageContaining("is out of range for java.lang.Float");

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(100.0), Float.class))
                        .returns(100.0F, JsonElement::getAsFloat))
                .as("in bound double input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> sanitizer.sanitize(GSON.toJsonTree(Double.MAX_VALUE - 42.0), Float.class))
                .as("out of bound double input")
                .isInstanceOf(JsonSchemaDeserializationException.class)
                .hasMessageContaining("is out of range for java.lang.Float");

        assertThatCode(
                () -> sanitizer.sanitize(GSON.toJsonTree(new ArrayList<>()), Float.class))
                .as("empty list input")
                .isInstanceOf(JsonSchemaDeserializationException.class)
                .hasMessageContaining("is not convertable to java.lang.Float, got java.util.List: []");
    }

    @Test
    public void test_defaultSanitizer_sanitize_double() {
        Gson GSON = new Gson();
        DefaultJsonSchemaSanitizer sanitizer = DefaultJsonSchemaSanitizer.builder().build();
        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(42.0), Double.class))
                        .returns(42.0, JsonElement::getAsNumber))
                .as("double input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> {
                    assertThat(sanitizer.sanitize(GSON.toJsonTree(0.0), Double.class))
                            .isEqualTo(GSON.toJsonTree(0.0));
                    assertThat(sanitizer.sanitize(GSON.toJsonTree(Double.MAX_VALUE), Double.class))
                            .isEqualTo(GSON.toJsonTree(Double.MAX_VALUE));
                    assertThat(sanitizer.sanitize(GSON.toJsonTree(Double.MIN_VALUE), Double.class))
                            .isEqualTo(GSON.toJsonTree(Double.MIN_VALUE));
                })
                .as("zero/max/min double input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree("42.0"), Double.class))
                        .returns(42.0, JsonElement::getAsNumber))
                .as("string input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(null, Double.class))
                        .isEqualTo(JsonNull.INSTANCE))
                .as("null input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(null), Double.class))
                        .isEqualTo(JsonNull.INSTANCE))
                .as("JsonNull input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(100), Double.class))
                        .returns(100.0, JsonElement::getAsNumber))
                .as("integer input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(100.0F), Double.class))
                        .returns(100.0, JsonElement::getAsNumber))
                .as("float input")
                .doesNotThrowAnyException();
    }

    @Test
    public void test_defaultSanitizer_sanitize_enum() {
        DefaultJsonSchemaSanitizer sanitizer = DefaultJsonSchemaSanitizer.builder().build();
        assertThatCode(
                () ->assertThat(sanitizer.sanitize(GSON.toJsonTree("A"), ExampleEnum.class))
                        .returns("A", JsonElement::getAsString))
                .as("string input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(ExampleEnum.A), ExampleEnum.class))
                        .returns("A", JsonElement::getAsString))
                .as("enum input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> sanitizer.sanitize(GSON.toJsonTree("D"), ExampleEnum.class))
                .as("invalid enum input")
                .isInstanceOf(JsonSchemaDeserializationException.class)
                .hasMessageContaining("has invalid enum value for");
    }

    @Test
    public void test_defaultSanitizer_sanitize_bigInteger() {
        DefaultJsonSchemaSanitizer sanitizer = DefaultJsonSchemaSanitizer.builder().build();
        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(42), BigInteger.class))
                        .returns(new BigInteger("42"), JsonElement::getAsNumber))
                .as("integer input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(0), BigInteger.class))
                        .returns(new BigInteger("0"), JsonElement::getAsNumber))
                .as("zero integer input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(Integer.MAX_VALUE), BigInteger.class))
                        .returns(BigInteger.valueOf(Integer.MAX_VALUE), JsonElement::getAsNumber))
                .as("max integer input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(Integer.MIN_VALUE), BigInteger.class))
                        .returns(BigInteger.valueOf(Integer.MIN_VALUE), JsonElement::getAsNumber))
                .as("min integer input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree("42"), BigInteger.class))
                        .returns(new BigInteger("42"), JsonElement::getAsNumber))
                .as("string input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(null, BigInteger.class))
                        .isEqualTo(JsonNull.INSTANCE))
                .as("null input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(null), BigInteger.class))
                        .isEqualTo(JsonNull.INSTANCE))
                .as("JsonNull input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(100L), BigInteger.class))
                        .isEqualTo(GSON.toJsonTree(new BigInteger("100")))
                )
                .as("in bound long input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(Long.MAX_VALUE), BigInteger.class))
                        .returns(BigInteger.valueOf(Long.MAX_VALUE), JsonElement::getAsNumber))
                .as("max long input")
                .doesNotThrowAnyException();
    }

    @Test
    public void test_defaultSanitizer_sanitize_bigDecimal() {
        Gson GSON = new Gson();
        DefaultJsonSchemaSanitizer sanitizer = DefaultJsonSchemaSanitizer.builder().build();
        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(42), BigDecimal.class))
                        .returns(new BigDecimal("42"), JsonElement::getAsNumber))
                .as("integer input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(0), BigDecimal.class))
                        .returns(new BigDecimal("0"), JsonElement::getAsNumber))
                .as("zero integer input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(Integer.MAX_VALUE), BigDecimal.class))
                        .returns(new BigDecimal(Integer.MAX_VALUE), JsonElement::getAsNumber))
                .as("max integer input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(Integer.MIN_VALUE), BigDecimal.class))
                        .returns(new BigDecimal(Integer.MIN_VALUE), JsonElement::getAsNumber))
                .as("min integer input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree("42"), BigDecimal.class))
                        .returns(new BigDecimal("42.0"), JsonElement::getAsNumber))
                .as("string input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(null, BigDecimal.class))
                        .isEqualTo(JsonNull.INSTANCE))
                .as("null input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(null), BigDecimal.class))
                        .isEqualTo(JsonNull.INSTANCE))
                .as("JsonNull input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(100L), BigDecimal.class))
                        .isEqualTo(GSON.toJsonTree(new BigDecimal("100")))
                )
                .as("in bound long input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(Long.MAX_VALUE), BigDecimal.class))
                        .returns(new BigDecimal(Long.MAX_VALUE), JsonElement::getAsNumber))
                .as("max long input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(100.0), BigDecimal.class))
                        .returns(new BigDecimal("100.0"), JsonElement::getAsNumber))
                .as("double input")
                .doesNotThrowAnyException();
    }

    @Test
    public void test_defaultSanitizer_sanitize_integerList() {
        DefaultJsonSchemaSanitizer sanitizer = DefaultJsonSchemaSanitizer.builder().build();
        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(Arrays.asList(1, 2, 3)), List.class))
                        .returns(GSON.toJsonTree(Arrays.asList(1, 2, 3)), JsonElement::getAsJsonArray))
                .as("list of integers input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(Collections.emptyList()), List.class))
                        .returns(GSON.toJsonTree(Collections.emptyList()), JsonElement::getAsJsonArray))
                .as("empty list input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> sanitizer.sanitize(GSON.toJsonTree(42), List.class))
                .as("number input")
                .isInstanceOf(JsonSchemaDeserializationException.class)
                .hasMessageContaining("is not convertable to java.util.List, got java.lang.Number: 42");

        assertThatCode(
                () -> sanitizer.sanitize(GSON.toJsonTree("string"), List.class))
                .as("string input")
                .isInstanceOf(JsonSchemaDeserializationException.class)
                .hasMessageContaining("is not convertable to java.util.List, got java.lang.String: \"string\"");

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(null, List.class))
                        .isEqualTo(JsonNull.INSTANCE))
                .as("null input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(null), List.class))
                        .isEqualTo(JsonNull.INSTANCE))
                .as("JsonNull input")
                .doesNotThrowAnyException();
    }

    @Test
    public void test_defaultSanitizer_sanitize_nestedIntegers() {
        Gson GSON = new Gson();
        DefaultJsonSchemaSanitizer sanitizer = DefaultJsonSchemaSanitizer.builder().build();
        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(Arrays.asList(Arrays.asList(1, 2), Arrays.asList(3, 4))), List.class))
                        .isEqualTo(GSON.toJsonTree(Arrays.asList(Arrays.asList(1, 2), Arrays.asList(3, 4)))))
                .as("nested list of integers input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(Collections.emptyList()), List.class))
                        .isEqualTo(GSON.toJsonTree(Collections.emptyList())))
                .as("empty list input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> sanitizer.sanitize(GSON.toJsonTree(42), List.class))
                .as("number input")
                .isInstanceOf(JsonSchemaDeserializationException.class)
                .hasMessageContaining("is not convertable to java.util.List, got java.lang.Number: 42");

        assertThatCode(
                () -> sanitizer.sanitize(GSON.toJsonTree("string"), List.class))
                .as("string input")
                .isInstanceOf(JsonSchemaDeserializationException.class)
                .hasMessageContaining("is not convertable to java.util.List, got java.lang.String: \"string\"");

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(null, List.class))
                        .isEqualTo(JsonNull.INSTANCE))
                .as("null input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(null), List.class))
                        .isEqualTo(JsonNull.INSTANCE))
                .as("JsonNull input")
                .doesNotThrowAnyException();
    }

    @Test
    public void test_defaultSanitizer_sanitize_mapping() {
        Gson GSON = new Gson();
        DefaultJsonSchemaSanitizer sanitizer = DefaultJsonSchemaSanitizer.builder().build();
        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(mapOf(entry("a", 1), entry("b", 2))), Map.class))
                        .isEqualTo(GSON.toJsonTree(mapOf(entry("a", 1), entry("b", 2)))))
                .as("map of integers input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(Collections.emptyMap()), Map.class))
                        .isEqualTo(GSON.toJsonTree(Collections.emptyMap()))
                )
                .as("empty map input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> sanitizer.sanitize(GSON.toJsonTree(42), Map.class))
                .as("number input")
                .isInstanceOf(JsonSchemaDeserializationException.class)
                .hasMessageContaining("is not convertable to java.util.Map, got java.lang.Number: 42");

        assertThatCode(
                () -> sanitizer.sanitize(GSON.toJsonTree("string"), Map.class))
                .as("string input")
                .isInstanceOf(JsonSchemaDeserializationException.class)
                .hasMessageContaining("is not convertable to java.util.Map, got java.lang.String: \"string\"");

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(null, Map.class))
                        .isEqualTo(JsonNull.INSTANCE)
                )
                .as("null input")
                .doesNotThrowAnyException();

        assertThatCode(
                () -> assertThat(sanitizer.sanitize(GSON.toJsonTree(null), Map.class))
                        .isEqualTo(JsonNull.INSTANCE)
                )
                .as("JsonNull input")
                .doesNotThrowAnyException();
    }
}
