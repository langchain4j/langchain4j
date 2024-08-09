package dev.langchain4j.model.sparkdesk;

import com.google.common.base.Strings;

import static dev.langchain4j.internal.Exceptions.illegalArgument;

public abstract class SparkUtils {
    public static String isNullOrEmpty(String str, String msg) {
        if (Strings.isNullOrEmpty(str)) {
            throw illegalArgument(msg);
        }
        return str;
    }
}
