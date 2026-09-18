package dev.langchain4j.http.client;

import static dev.langchain4j.internal.Exceptions.illegalArgument;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Internal helpers for storing HTTP headers.
 */
final class HttpHeaders {

    private HttpHeaders() {}

    /**
     * Returns an unmodifiable copy of the provided headers whose lookups
     * ({@link Map#get(Object)}, {@link Map#containsKey(Object)}) are case-insensitive,
     * as required by <a href="https://www.rfc-editor.org/rfc/rfc9110#section-5.1">RFC 9110</a>.
     * Returns an empty map if the provided headers are {@code null}.
     * <p>
     * When the same header name is present more than once in different cases, the last one wins.
     *
     * @param headers The headers to copy.
     * @return The copy of the provided headers or an empty map.
     * @throws IllegalArgumentException if any header name is {@code null}.
     */
    static Map<String, List<String>> copyCaseInsensitive(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((name, values) -> {
            if (name == null) {
                throw illegalArgument("Header name cannot be null");
            }
            copy.put(name, values);
        });
        return Collections.unmodifiableMap(copy);
    }
}
