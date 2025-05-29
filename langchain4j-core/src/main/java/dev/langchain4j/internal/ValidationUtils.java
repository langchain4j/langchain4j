package dev.langchain4j.internal;

import dev.langchain4j.Internal;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Exceptions.illegalArgument;

/**
 * Utility class for validating method arguments.
 */
@Internal
public class ValidationUtils {

    private ValidationUtils() {}

    /**
     * Ensure that the two values are equal.
     * @param lhs the left hand side value.
     * @param rhs the right hand side value.
     * @param format the format string for the exception message.
     * @param args the format arguments for the exception message.
     */
    public static void ensureEq(Object lhs, Object rhs, String format, Object... args) {
        if (!Objects.equals(lhs, rhs)) {
            throw illegalArgument(format, args);
        }
    }

    /**
     * Ensures that the given object is not null.
     * @param object The object to check.
     * @param name The name of the object to be used in the exception message.
     * @return The object if it is not null.
     * @param <T> The type of the object.
     * @throws IllegalArgumentException if the object is null.
     */
    public static <T> T ensureNotNull(T object, String name) {
        return ensureNotNull(object, "%s cannot be null", name);
    }

    /**
     * Ensures that the given object is not null.
     * @param object The object to check.
     * @param format The format of the exception message.
     * @param args The arguments for the exception message.
     * @return The object if it is not null.
     * @param <T> The type of the object.
     */
    public static <T> T ensureNotNull(T object, String format, Object... args) {
        if (object == null) {
            throw illegalArgument(format, args);
        }
        return object;
    }

    /**
     * Ensures that the given collection is not null and not empty.
     * @param collection The collection to check.
     * @param name The name of the collection to be used in the exception message.
     * @return The collection if it is not null and not empty.
     * @param <T> The type of the collection.
     *           @throws IllegalArgumentException if the collection is null or empty.
     */
    public static <T extends Collection<?>> T ensureNotEmpty(T collection, String name) {
        if (collection == null || collection.isEmpty()) {
            throw illegalArgument("%s cannot be null or empty", name);
        }

        return collection;
    }

    /**
     * Ensures that the given array is not null and not empty.
     * @param array The array to check.
     * @param name The name of the array to be used in the exception message.
     * @return The array if it is not null and not empty.
     * @param <T> The component type of the array.
     * @throws IllegalArgumentException if the array is null or empty.
     */
    public static <T> T[] ensureNotEmpty(T[] array, String name) {
        if (array == null || array.length == 0) {
            throw illegalArgument("%s cannot be null or empty", name);
        }

        return array;
    }

    /**
     * Ensures that the given map is not null and not empty.
     *
     * @param map  The map to check.
     * @param name The name of the map to be used in the exception message.
     * @param <K>  The type of the key.
     * @param <V>  The type of the value.
     * @return The map if it is not null and not empty.
     * @throws IllegalArgumentException if the collection is null or empty.
     */
    public static <K, V> Map<K, V> ensureNotEmpty(Map<K, V> map, String name) {
        if (map == null || map.isEmpty()) {
            throw illegalArgument("%s cannot be null or empty", name);
        }

        return map;
    }

    /**
     * Ensures that the given string is not null and not blank.
     * @param string The string to check.
     * @param name The name of the string to be used in the exception message.
     * @return The string if it is not null and not blank.
     * @throws IllegalArgumentException if the string is null or blank.
     */
    public static String ensureNotBlank(String string, String name) {
        if (string == null || string.trim().isEmpty()) {
            throw illegalArgument("%s cannot be null or blank", name);
        }

        return string;
    }

    /**
     * Ensures that the given expression is true.
     * @param expression The expression to check.
     * @param msg The message to be used in the exception.
     * @throws IllegalArgumentException if the expression is false.
     */
    public static void ensureTrue(boolean expression, String msg) {
        if (!expression) {
            throw illegalArgument(msg);
        }
    }

    /**
     * Ensures that the given expression is true.
     * @param i The expression to check.
     * @param name The message to be used in the exception.
     * @return The value if it is greater than zero.
     * @throws IllegalArgumentException if the expression is false.
     */
    public static int ensureGreaterThanZero(Integer i, String name) {
        if (i == null || i <= 0) {
            throw illegalArgument("%s must be greater than zero, but is: %s", name, i);
        }

        return i;
    }

    /**
     * Ensures that the given expression is true.
     * @param i The expression to check.
     * @param name The message to be used in the exception.
     * @return The value if it is greater than zero.
     * @throws IllegalArgumentException if the expression is false.
     */
    public static double ensureGreaterThanZero(Double i, String name) {
        if (i == null || i <= 0) {
            throw illegalArgument("%s must be greater than zero, but is: %s", name, i);
        }

        return i;
    }

    /**
     * Ensures that the given Double value is in {@code [min, max]}.
     * @param d The value to check.
     * @param min The minimum value.
     * @param max The maximum value.
     * @param name The value name to be used in the exception.
     * @return The value if it is in {@code [min, max]}.
     * @throws IllegalArgumentException if the value is not in {@code [min, max]}.
     */
    public static double ensureBetween(Double d, double min, double max, String name) {
        if (d == null || d < min || d > max) {
            throw illegalArgument("%s must be between %s and %s, but is: %s", name, min, max, d);
        }
        return d;
    }

    /**
     * Ensures that the given Integer value is in {@code [min, max]}.
     * @param i The value to check.
     * @param min The minimum value.
     * @param max The maximum value.
     * @param name The value name to be used in the exception.
     * @return The value if it is in {@code [min, max]}.
     * @throws IllegalArgumentException if the value is not in {@code [min, max]}.
     */
    public static int ensureBetween(Integer i, int min, int max, String name) {
        if (i == null || i < min || i > max) {
            throw illegalArgument("%s must be between %s and %s, but is: %s", name, min, max, i);
        }
        return i;
    }
    /**
     * Ensures that the given Long value is in {@code [min, max]}.
     * @param i The value to check.
     * @param min The minimum value.
     * @param max The maximum value.
     * @param name The value name to be used in the exception.
     * @return The value if it is in {@code [min, max]}.
     * @throws IllegalArgumentException if the value is not in {@code [min, max]}.
     */
    public static long ensureBetween(Long i, long min, long max, String name) {
        if (i == null || i < min || i > max) {
            throw illegalArgument("%s must be between %s and %s, but is: %s", name, min, max, i);
        }
        return i;
    }
}
