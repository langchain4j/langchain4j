package dev.langchain4j.agent.tool;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

public class JsonSchemaProperty {

    public static final JsonSchemaProperty STRING = type("string");
    public static final JsonSchemaProperty INTEGER = type("integer");
    public static final JsonSchemaProperty NUMBER = type("number");
    public static final JsonSchemaProperty OBJECT = type("object");
    public static final JsonSchemaProperty ARRAY = type("array");
    public static final JsonSchemaProperty BOOLEAN = type("boolean");
    public static final JsonSchemaProperty NULL = type("null");

    private final String key;
    private final Object value;

    public JsonSchemaProperty(String key, Object value) {
        this.key = key;
        this.value = value;
    }

    public String key() {
        return key;
    }

    public Object value() {
        return value;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof JsonSchemaProperty
                && equalTo((JsonSchemaProperty) another);
    }

    private boolean equalTo(JsonSchemaProperty another) {
        return Objects.equals(key, another.key)
                && Objects.equals(value, another.value);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(key);
        h += (h << 5) + Objects.hashCode(value);
        return h;
    }

    @Override
    public String toString() {
        return "JsonSchemaProperty {"
                + " key = " + quoted(key)
                + ", value = " + value
                + " }";
    }

    public static JsonSchemaProperty from(String key, Object value) {
        return new JsonSchemaProperty(key, value);
    }

    public static JsonSchemaProperty property(String key, Object value) {
        return from(key, value);
    }

    public static JsonSchemaProperty type(String value) {
        return from("type", value);
    }

    public static JsonSchemaProperty description(String value) {
        return from("description", value);
    }

    public static JsonSchemaProperty enums(String... enumValues) {
        return from("enum", enumValues);
    }

    public static JsonSchemaProperty enums(Object... enumValues) {
        for (Object enumValue : enumValues) {
            if (!enumValue.getClass().isEnum()) {
                throw new RuntimeException("Value " + enumValue.getClass().getName() + " should be enum");
            }
        }

        return from("enum", enumValues);
    }

    public static JsonSchemaProperty enums(Class<?> enumClass) {
        if (!enumClass.isEnum()) {
            throw new RuntimeException("Class " + enumClass.getName() + " should be enum");
        }

        return from("enum", enumClass.getEnumConstants());
    }
}
