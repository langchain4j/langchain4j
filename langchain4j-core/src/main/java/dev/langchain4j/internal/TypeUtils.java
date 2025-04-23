package dev.langchain4j.internal;

import dev.langchain4j.Internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;

@Internal
public class TypeUtils {

    public static boolean isJsonInteger(Class<?> type) {
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

    public static boolean isJsonNumber(Class<?> type) {
        return type == float.class
                || type == Float.class
                || type == double.class
                || type == Double.class
                || type == BigDecimal.class;
    }

    public static boolean isJsonBoolean(Class<?> type) {
        return type == boolean.class || type == Boolean.class;
    }

    public static boolean isJsonString(Class<?> type) {
        return type == String.class
                || type == char.class
                || type == Character.class
                || CharSequence.class.isAssignableFrom(type)
                || type == UUID.class;
    }

    public static boolean isJsonArray(Class<?> type) {
        return type.isArray() || Iterable.class.isAssignableFrom(type);
    }
}
