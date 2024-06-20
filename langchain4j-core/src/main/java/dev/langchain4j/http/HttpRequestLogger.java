package dev.langchain4j.http;

import dev.langchain4j.Experimental;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;

@Slf4j
@Experimental
class HttpRequestLogger {

    private static final Set<String> COMMON_SECRET_HEADERS =
            new HashSet<>(asList("authorization", "x-api-key", "x-auth-token"));

    static void log(HttpRequest httpRequest) {
        try {
            // TODO pretty print body?
            log.debug("Request:\n- method: {}\n- url: {}\n- headers: {}\n- body: {}",
                    httpRequest.method(), httpRequest.url(), format(httpRequest.headers()), httpRequest.body());
        } catch (Exception e) {
            log.warn("Error while logging request: {}", e.getMessage());
        }
    }

    static String format(Map<String, String> headers) {
        return headers.entrySet().stream()
                .map(header -> format(header.getKey(), header.getValue()))
                .collect(joining(", "));
    }

    static String format(String headerKey, String headerValue) {
        if (COMMON_SECRET_HEADERS.contains(headerKey.toLowerCase())) {
            headerValue = maskSecretKey(headerValue);
        }
        return String.format("[%s: %s]", headerKey, headerValue);
    }

    static String maskSecretKey(String key) {
        if (isNullOrBlank(key)) {
            return key;
        }

        if (key.length() >= 7) {
            return key.substring(0, 5) + "..." + key.substring(key.length() - 2);
        } else {
            return "..."; // to short to be masked
        }
    }
}
