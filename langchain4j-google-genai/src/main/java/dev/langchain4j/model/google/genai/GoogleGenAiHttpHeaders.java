package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.CustomHeadersUtils.mergeCustomHeaders;

import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.HttpOptions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The google-genai SDK merges headers by their exact name, while HTTP header names are case-insensitive.
 * A header that differs from an earlier one only in case would be sent twice, and which value wins is not
 * defined. So every header name is first changed to the spelling of a header with the same name that is
 * already known: one of the SDK's own headers, or a header set when the client was built.
 * <p>
 * For {@code user-agent} and {@code x-goog-api-client}, the SDK appends the new value to its own value
 * instead of replacing it.
 */
final class GoogleGenAiHttpHeaders {

    static final String USER_AGENT = "user-agent";

    private static final List<String> SDK_HEADER_NAMES = List.of("Content-Type", USER_AGENT, "x-goog-api-client");

    private GoogleGenAiHttpHeaders() {}

    /**
     * Returns the headers to set when the client is built: the custom headers of the model builder, plus
     * {@code user-agent: LangChain4j} unless the custom headers already contain a user agent.
     */
    static Map<String, String> clientHeaders(@Nullable Map<String, String> customHeaders) {
        Map<String, String> headers = withKnownNames(customHeaders, SDK_HEADER_NAMES);
        headers.putIfAbsent(USER_AGENT, "LangChain4j");
        return headers;
    }

    /**
     * Returns the config with the per-request headers added to its {@link HttpOptions},
     * where they win over the headers that the config already has.
     */
    static GenerateContentConfig withPerRequestHeaders(
            GenerateContentConfig config, Map<String, String> requestHeaders, Map<String, String> clientHeaders) {
        if (requestHeaders.isEmpty()) {
            return config;
        }

        HttpOptions httpOptions = config.httpOptions().orElseGet(() -> HttpOptions.builder()
                .build());
        Map<String, String> configHeaders = httpOptions.headers().orElse(Map.of());

        List<String> knownNames = new ArrayList<>(SDK_HEADER_NAMES);
        knownNames.addAll(clientHeaders.keySet());
        knownNames.addAll(configHeaders.keySet());

        Map<String, String> headers = mergeCustomHeaders(configHeaders, withKnownNames(requestHeaders, knownNames));

        return config.toBuilder()
                .httpOptions(httpOptions.toBuilder().headers(headers).build())
                .build();
    }

    private static Map<String, String> withKnownNames(
            @Nullable Map<String, String> headers, Collection<String> knownNames) {
        Map<String, String> result = new LinkedHashMap<>();
        if (headers == null) {
            return result;
        }
        headers.forEach((name, value) -> {
            String knownName = knownNames.stream()
                    .filter(name::equalsIgnoreCase)
                    .findFirst()
                    .orElse(name);
            result.put(knownName, value);
        });
        return result;
    }
}
