package dev.langchain4j.web.search.google.customsearch;

import dev.langchain4j.internal.UriUtils;
import java.net.URI;

/**
 * Utility class for Google Custom Web Search operations.
 *
 * @deprecated Use {@link UriUtils} instead. This class delegates to {@link UriUtils#createUriSafely(String)}.
 */
@Deprecated
final class GoogleCustomWebSearchUtils {

    private GoogleCustomWebSearchUtils() {}

    /**
     * Creates a URI from the given string, handling illegal characters safely.
     *
     * @param uriString the input string to convert to a URI
     * @return a URI object, or null if the input is null or blank, or an empty URI if invalid
     * @deprecated Use {@link UriUtils#createUriSafely(String)} instead.
     */
    @Deprecated
    public static URI createUriSafely(String uriString) {
        return UriUtils.createUriSafely(uriString);
    }
}
