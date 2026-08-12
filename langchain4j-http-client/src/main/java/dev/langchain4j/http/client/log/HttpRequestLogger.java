package dev.langchain4j.http.client.log;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.Internal;
import dev.langchain4j.http.client.HttpRequest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;

@Internal
class HttpRequestLogger {

    /**
     * A header whose name contains any of these is assumed to carry a credential.
     * Matching deliberately errs towards masking too much: a masked value that turns out to be
     * harmless costs one debugging round trip, an unmasked credential costs a key rotation.
     */
    private static final List<String> SECRET_HEADER_NAME_KEYWORDS = asList(
            "auth",
            "api-key",
            "api_key",
            "apikey",
            "subscription-key",
            "token",
            "secret",
            "password",
            "credential",
            "cookie");

    static void log(Logger log, HttpRequest httpRequest) {
        try {
            log.info("""
                            HTTP request:
                            - method: {}
                            - url: {}
                            - headers: {}
                            - body: {}
                            """, httpRequest.method(), httpRequest.url(), format(httpRequest.headers()), httpRequest.body());
        } catch (Exception e) {
            log.warn("Exception occurred while logging HTTP request: {}", e.getMessage());
        }
    }

    static String format(Map<String, List<String>> headers) {
        return headers.entrySet().stream()
                .map(header -> format(header.getKey(), header.getValue()))
                .collect(joining(", "));
    }

    static String format(String headerKey, List<String> headerValues) {
        if (isSecretHeader(headerKey)) {
            headerValues =
                    headerValues.stream().map(HttpRequestLogger::maskSecretKey).collect(toList());
        }

        if (headerValues.size() == 1) {
            return String.format("[%s: %s]", headerKey, headerValues.get(0));
        } else {
            return String.format("[%s: %s]", headerKey, headerValues);
        }
    }

    private static boolean isSecretHeader(String headerKey) {
        String lowerCaseHeaderKey = headerKey.toLowerCase(Locale.ROOT);
        return SECRET_HEADER_NAME_KEYWORDS.stream().anyMatch(lowerCaseHeaderKey::contains);
    }

    static String maskSecretKey(String key) {
        if (isNullOrBlank(key)) {
            return key;
        }

        if (key.length() >= 7) {
            return key.substring(0, 5) + "..." + key.substring(key.length() - 2);
        } else {
            return "..."; // too short to be masked
        }
    }
}
