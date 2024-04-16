package dev.langchain4j.jsonschema;

import java.math.BigDecimal;
import java.math.BigInteger;

public class JsonSchemaUtil {
    enum JsonSchemaElementType {
        BOOLEAN,
        INTEGER,
        NUMBER,
        STRING,
        ENUM,
        ARRAY,
        OBJECT,
    }

    static JsonSchemaElementType getJsonSchemaElementType(Class<?> type) {
        if (type == String.class) return JsonSchemaElementType.STRING;
        if (isBoolean(type)) return JsonSchemaElementType.BOOLEAN;
        if (isInteger(type)) return JsonSchemaElementType.INTEGER;
        if (isDecimal(type)) return JsonSchemaElementType.NUMBER;
        if (type.isEnum()) return JsonSchemaElementType.ENUM;
        if (type.isArray() || Iterable.class.isAssignableFrom(type))
            return JsonSchemaElementType.ARRAY;
        return JsonSchemaElementType.OBJECT;
    }

    static boolean isDecimal(Class<?> type) {
        return type == float.class
                || type == Float.class
                || type == double.class
                || type == Double.class
                || type == BigDecimal.class;
    }

    static boolean isInteger(Class<?> type) {
        return type == byte.class
                || type == Byte.class
                || type == short.class
                || type == Short.class
                || type == int.class
                || type == Integer.class
                || type == long.class
                || type == Long.class
                || type == BigInteger.class;
    }

    static boolean isBoolean(Class<?> type) {
        return type == boolean.class || type == Boolean.class;
    }
}
