package dev.langchain4j.store.embedding.vearch;

import java.util.Collection;
import java.util.Map;

public class MyUtils {

    public static boolean strNotBlank(CharSequence str) {
        return !strBlank(str);
    }

    public static boolean strBlank(CharSequence str) {
        int length;

        if ((str == null) || ((length = str.length()) == 0)) {
            return true;
        }

        for (int i = 0; i < length; i++) {
            if (!charBlank(str.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    public static boolean charBlank(char c) {
        return charBlank((int) c);
    }

    public static boolean charBlank(int c) {
        return Character.isWhitespace(c)
                || Character.isSpaceChar(c)
                || c == '\ufeff'
                || c == '\u202a'
                || c == '\u0000';
    }

    public static boolean collEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean collNotEmpty(Collection<?> collection) {
        return !collEmpty(collection);
    }

    public static boolean mapNotEmpty(Map<?, ?> map) {
        return null != map && !map.isEmpty();
    }

    public static boolean mapEmpty(Map<?, ?> map) {
        return null == map || map.isEmpty();
    }

}
