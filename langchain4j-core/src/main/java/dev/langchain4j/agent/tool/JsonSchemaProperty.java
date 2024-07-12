package dev.langchain4j.agent.tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;
import static java.util.Collections.singletonMap;

/**
 * Represents a property in a JSON schema.
 */
public class JsonSchemaProperty {

    /**
     * A property with key "type" and value "string".
     */
    public static final JsonSchemaProperty STRING = type("string");

    /**
     * A property with key "type" and value "integer".
     */
    public static final JsonSchemaProperty INTEGER = type("integer");

    /**
     * A property with key "type" and value "number".
     */
    public static final JsonSchemaProperty NUMBER = type("number");

    /**
     * A property with key "type" and value "object".
     */
    public static final JsonSchemaProperty OBJECT = type("object");

    /**
     * A property with key "type" and value "array".
     */
    public static final JsonSchemaProperty ARRAY = type("array");

    /**
     * A property with key "type" and value "boolean".
     */
    public static final JsonSchemaProperty BOOLEAN = type("boolean");

    /**
     * A property with key "type" and value "null".
     */
    public static final JsonSchemaProperty NULL = type("null");

    private final String key;
    private final Object value;

    /**
     * Construct a property with key and value.
     *
     * @param key   the key.
     * @param value the value.
     */
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
        return another instanceof JsonSchemaProperty
                && equalTo((JsonSchemaProperty) another);
    }

    /**
     * Utility method to compare two {@link JsonSchemaProperty} instances.
     *
     * @param another the other instance.
     * @return true if the two instances are equal.
     */
    private boolean equalTo(JsonSchemaProperty another) {
        if (!Objects.equals(key, another.key)) return false;

        if (value instanceof Object[] && another.value instanceof Object[]) {
            return Arrays.equals((Object[]) value, (Object[]) another.value);
        }

        return Objects.equals(value, another.value);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(key);
        int v = (value instanceof Object[]) ? Arrays.hashCode((Object[]) value) : Objects.hashCode(value);
        h += (h << 5) + v;
        return h;
    }

    @Override
    public String toString() {
        String valueString = (value instanceof Object[]) ? Arrays.toString((Object[]) value) : value.toString();
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
     */
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
     */
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
     */
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
     */
    public static JsonSchemaProperty description(String value) {
        return from("description", value);
    }

    /**
     * Construct a property with key "enum" and value enumValues.
     *
     * @param enumValues enum values as strings. For example: {@code enums("CELSIUS", "FAHRENHEIT")}
     * @return a property with key "enum" and value enumValues
     */
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
     */
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
     */
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
     */
    public static JsonSchemaProperty items(JsonSchemaProperty type) {
        return from("items", singletonMap(type.key, type.value));
    }

    public static JsonSchemaProperty objectItems(JsonSchemaProperty type) {
        Map<String,Object> map = new HashMap<>();
        map.put("type", "object");
        map.put(type.key, type.value);
        return from("items", map);
    }
}
