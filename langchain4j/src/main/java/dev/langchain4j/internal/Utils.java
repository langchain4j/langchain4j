package dev.langchain4j.internal;

import java.util.Arrays;
import java.util.List;

public class Utils {

    public static <T> List<T> list(T... elements) {
        return Arrays.asList(elements);
    }
}
