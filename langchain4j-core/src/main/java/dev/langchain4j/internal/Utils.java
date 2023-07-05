package dev.langchain4j.internal;

import java.util.Arrays;
import java.util.List;

public class Utils {

    public static boolean isNullOrBlank(String string) {
        return string == null || string.trim().isEmpty();
    }

    public static <T> List<T> list(T... elements) {
        return Arrays.asList(elements);
    }
}
