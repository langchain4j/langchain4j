package dev.langchain4j.agent.tool;

import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;
import static java.util.Collections.singletonMap;

/**
 * Represents a property in a JSON schema.
 *
 * @deprecated please use the new {@link JsonSchemaElement} API instead to define the schema for tool parameters
 */
@Deprecated(forRemoval = true)
public class JsonSchemaProperty {

    /**
     * A property with key "type" and value "string".
     *
     * @deprecated please use {@link JsonStringSchema#JsonStringSchema()} instead
     */
    @Deprecated(forRemoval = true)
    public static final JsonSchemaProperty STRING = type("string");

    /**
     * A property with key "type" and value "integer".
     *
     * @deprecated please use {@link JsonIntegerSchema#JsonIntegerSchema()} instead
     */
    @Deprecated(forRemoval = true)
    public static final JsonSchemaProperty INTEGER = type("integer");

    /**
     * A property with key "type" and value "number".
     *
     * @deprecated please use {@link JsonNumberSchema#JsonNumberSchema()} instead
     */
    @Deprecated(forRemoval = true)
    public static final JsonSchemaProperty NUMBER = type("number");

    /**
     * A property with key "type" and value "object".
     *
     * @deprecated please use {@link JsonObjectSchema#builder} instead
     */
    @Deprecated(forRemoval = true)
    public static final JsonSchemaProperty OBJECT = type("object");

    /**
     * A property with key "type" and value "array".
     *
     * @deprecated please use {@link JsonArraySchema#builder} instead
     */
    @Deprecated(forRemoval = true)
    public static final JsonSchemaProperty ARRAY = type("array");

    /**
     * A property with key "type" and value "boolean".
     *
     * @deprecated please use {@link JsonBooleanSchema#JsonBooleanSchema()} instead
     */
    @Deprecated(forRemoval = true)
    public static final JsonSchemaProperty BOOLEAN = type("boolean");

    /**
     * A property with key "type" and value "null".
     */
    @Deprecated(forRemoval = true)
    public static final JsonSchemaProperty NULL = type("null");

    private final String key;
    private final Object value;

    /**
     * Construct a property with key and value.
     *
     * @param key   the key.
     * @param value the value.
     * @deprecated please use the new {@link JsonSchemaElement} API instead to define the schema for tool parameters
     */
    @Deprecated(forRemoval = true)
    public JsonSchemaProperty(String key, Object value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Get the key.
     *
     * @return the key.
     */
    public String key() {
        return key;
    }

    /**
     * Get the value.
     *
     * @return the value.
     */
    public Object value() {
        return value;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof JsonSchemaProperty jsp
                && equalTo(jsp);
    }

    /**
     * Utility method to compare two {@link JsonSchemaProperty} instances.
     *
     * @param another the other instance.
     * @return true if the two instances are equal.
     */
    private boolean equalTo(JsonSchemaProperty another) {
        if (!Objects.equals(key, another.key)) return false;

        if (value instanceof Object[] objects && another.value instanceof Object[] objects1) {
            return Arrays.equals(objects, objects1);
        }

        return Objects.equals(value, another.value);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(key);
        int v = (value instanceof Object[] os) ? Arrays.hashCode(os) : Objects.hashCode(value);
        h += (h << 5) + v;
        return h;
    }

    @Override
    public String toString() {
        String valueString = (value instanceof Object[] os) ? Arrays.toString(os) : value.toString();
        return "JsonSchemaProperty {"
                + " key = " + quoted(key)
                + ", value = " + valueString
                + " }";
    }

    /**
     * Construct a property with key and value.
     *
     * <p>Equivalent to {@code new JsonSchemaProperty(key, value)}.
     *
     * @param key   the key.
     * @param value the value.
     * @return a property with key and value.
     * @deprecated please use the new {@link JsonSchemaElement} API instead to define the schema for tool parameters
     */
    @Deprecated(forRemoval = true)
    public static JsonSchemaProperty from(String key, Object value) {
        return new JsonSchemaProperty(key, value);
    }

    /**
     * Construct a property with key and value.
     *
     * <p>Equivalent to {@code new JsonSchemaProperty(key, value)}.
     *
     * @param key   the key.
     * @param value the value.
     * @return a property with key and value.
     * @deprecated please use the new {@link JsonSchemaElement} API instead to define the schema for tool parameters
     */
    @Deprecated(forRemoval = true)
    public static JsonSchemaProperty property(String key, Object value) {
        return from(key, value);
    }

    /**
     * Construct a property with key "type" and value.
     *
     * <p>Equivalent to {@code new JsonSchemaProperty("type", value)}.
     *
     * @param value the value.
     * @return a property with key and value.
     * @deprecated please use the new {@link JsonSchemaElement} API instead to define the schema for tool parameters
     */
    @Deprecated(forRemoval = true)
    public static JsonSchemaProperty type(String value) {
        return from("type", value);
    }

    /**
     * Construct a property with key "description" and value.
     *
     * <p>Equivalent to {@code new JsonSchemaProperty("description", value)}.
     *
     * @param value the value.
     * @return a property with key and value.
     * @deprecated please use the new {@link JsonSchemaElement} API instead to define the schema for tool parameters
     */
    @Deprecated(forRemoval = true)
    public static JsonSchemaProperty description(String value) {
        return from("description", value);
    }

    /**
     * Construct a property with key "enum" and value enumValues.
     *
     * @param enumValues enum values as strings. For example: {@code enums("CELSIUS", "FAHRENHEIT")}
     * @return a property with key "enum" and value enumValues
     * @deprecated please use {@link JsonEnumSchema} instead
     */
    @Deprecated(forRemoval = true)
    public static JsonSchemaProperty enums(String... enumValues) {
        return from("enum", enumValues);
    }

    /**
     * Construct a property with key "enum" and value enumValues.
     *
     * <p>Verifies that each value is a java class.
     *
     * @param enumValues enum values. For example: {@code enums(TemperatureUnit.CELSIUS, TemperatureUnit.FAHRENHEIT)}
     * @return a property with key "enum" and value enumValues
     * @deprecated please use {@link JsonEnumSchema} instead
     */
    @Deprecated(forRemoval = true)
    public static JsonSchemaProperty enums(Object... enumValues) {
        List<String> enumNames = new ArrayList<>();
        for (Object enumValue : enumValues) {
            if (!enumValue.getClass().isEnum()) {
                throw new RuntimeException("Value " + enumValue.getClass().getName() + " should be enum");
            }
            enumNames.add(((Enum<?>) enumValue).name());
        }
        return from("enum", enumNames);
    }

    /**
     * Construct a property with key "enum" and all enum values taken from enumClass.
     *
     * @param enumClass enum class. For example: {@code enums(TemperatureUnit.class)}
     * @return a property with key "enum" and values taken from enumClass
     * @deprecated please use {@link JsonEnumSchema} instead
     */
    @Deprecated(forRemoval = true)
    public static JsonSchemaProperty enums(Class<?> enumClass) {
        if (!enumClass.isEnum()) {
            throw new RuntimeException("Class " + enumClass.getName() + " should be enum");
        }
        return enums((Object[]) enumClass.getEnumConstants());
    }

    /**
     * Wraps the given type in a property with key "items".
     *
     * @param type the type
     * @return a property with key "items" and value type.
     * @deprecated please use {@link JsonArraySchema} instead
     */
    @Deprecated(forRemoval = true)
    public static JsonSchemaProperty items(JsonSchemaProperty type) {
        return from("items", singletonMap(type.key, type.value));
    }

    /**
     * @deprecated please use {@link JsonObjectSchema} instead
     */
    @Deprecated(forRemoval = true)
    public static JsonSchemaProperty objectItems(JsonSchemaProperty type) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "object");
        map.put(type.key, type.value);
        return from("items", map);
    }
}
