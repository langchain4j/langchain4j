package dev.langchain4j.internal;

import java.util.Collection;
import java.util.UUID;

public class Utils {

    public static boolean isNullOrBlank(String string) {
        return string == null || string.trim().isEmpty();
    }

    public static boolean isCollectionEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public static String repeat(String string, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(string);
        }
        return sb.toString();
    }

    public static String randomUUID() {
        return UUID.randomUUID().toString();
    }
}
