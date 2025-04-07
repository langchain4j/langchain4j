package dev.langchain4j.http.client.log;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.HttpRequest;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@Experimental
class HttpRequestLogger {

    private static final Set<String> COMMON_SECRET_HEADERS =
            new HashSet<>(asList("authorization", "x-api-key", "x-auth-token"));

    static void log(Logger log, HttpRequest httpRequest) {
        try {
            log.debug("""
                            HTTP request:
                            - method: {}
                            - url: {}
                            - headers: {}
                            - body: {}
                            """,
                    httpRequest.method(), httpRequest.url(), format(httpRequest.headers()), httpRequest.body());
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
        if (COMMON_SECRET_HEADERS.contains(headerKey.toLowerCase())) {
            headerValues = headerValues.stream()
                    .map(HttpRequestLogger::maskSecretKey)
                    .collect(toList());
        }

        if (headerValues.size() == 1) {
            return String.format("[%s: %s]", headerKey, headerValues.get(0));
        } else {
            return String.format("[%s: %s]", headerKey, headerValues);
        }
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
