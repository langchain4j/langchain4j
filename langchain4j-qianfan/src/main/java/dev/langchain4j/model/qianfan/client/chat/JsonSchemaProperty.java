package dev.langchain4j.model.qianfan.client.chat;


import java.util.Objects;

public class JsonSchemaProperty {
    public static final JsonSchemaProperty STRING = type("string");
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
        return this.key;
    }

    public Object value() {
        return this.value;
    }

    public boolean equals(Object another) {
        if (this == another) {
            return true;
        } else {
            return another instanceof JsonSchemaProperty
                    && this.equalTo((JsonSchemaProperty)another);
        }
    }

    private boolean equalTo(JsonSchemaProperty another) {
        return Objects.equals(this.key, another.key) && Objects.equals(this.value, another.value);
    }

    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(this.key);
        h += (h << 5) + Objects.hashCode(this.value);
        return h;
    }

    public String toString() {
        return "JsonSchemaProperty{key=" + this.key + ", value=" + this.value + "}";
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
        Object[] var1 = enumValues;
        int var2 = enumValues.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            Object enumValue = var1[var3];
            if (!enumValue.getClass().isEnum()) {
                throw new RuntimeException("Value " + enumValue.getClass().getName() + " should be enum");
            }
        }

        return from("enum", enumValues);
    }

    public static JsonSchemaProperty enums(Class<?> enumClass) {
        if (!enumClass.isEnum()) {
            throw new RuntimeException("Class " + enumClass.getName() + " should be enum");
        } else {
            return from("enum", enumClass.getEnumConstants());
        }
    }
}
