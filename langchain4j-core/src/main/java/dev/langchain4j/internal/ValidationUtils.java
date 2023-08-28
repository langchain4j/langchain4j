package dev.langchain4j.internal;

import java.util.Collection;

import static dev.langchain4j.internal.Exceptions.illegalArgument;

public class ValidationUtils {

    public static <T> T ensureNotNull(T object, String name) {
        if (object == null) {
            throw illegalArgument("%s cannot be null", name);
        }

        return object;
    }

    public static <T extends Collection<?>> T ensureNotEmpty(T collection, String name) {
        if (collection == null || collection.isEmpty()) {
            throw illegalArgument("%s cannot be null or empty", name);
        }

        return collection;
    }

    public static String ensureNotBlank(String string, String name) {
        if (string == null || string.trim().isEmpty()) {
            throw illegalArgument("%s cannot be null or blank", name);
        }

        return string;
    }

    public static int ensureGreaterThanZero(Integer i, String name) {
        if (i == null || i < 1) {
            throw illegalArgument("%s must be greater than 0", name);
        }

        return i;
    }
}
