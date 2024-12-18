package dev.langchain4j.model.huggingface.util;

import org.apache.commons.validator.routines.UrlValidator;

/**
 * Url utils. based on apache-commons lib
 */
public class UrlUtil {

    private UrlUtil() {}

    /**
     * Checks if url is valid
     * @param url String
     * @return boolean result
     */
    public static boolean isValidUrl(String url) {
        UrlValidator validator = new UrlValidator();
        return validator.isValid(url);
    }

    /**
     * Checks if url is NOT valid
     * @param url String
     * @return boolean result
     */
    public static boolean isNotValidUrl(String url) {
        return !isValidUrl(url);
    }
}
