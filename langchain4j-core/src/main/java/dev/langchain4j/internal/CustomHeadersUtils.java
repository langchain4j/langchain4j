package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.HttpParameters;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

@Internal
public class CustomHeadersUtils {

    private static final String USER_AGENT = "User-Agent";

    private CustomHeadersUtils() {}

    /**
     * Merges two sets of HTTP headers by name, ignoring case, as HTTP header names are case-insensitive.
     * When both contain a header with the same name, the one from {@code overrides} wins,
     * with the spelling of its name.
     *
     * @param headers   the headers to start from; {@code null} means none
     * @param overrides the headers that win over {@code headers}; {@code null} means none
     * @return a new unmodifiable map; empty if both are {@code null} or empty
     */
    public static Map<String, String> mergeCustomHeaders(
            @Nullable Map<String, String> headers, @Nullable Map<String, String> overrides) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (headers != null) {
            merged.putAll(headers);
        }
        if (overrides != null) {
            overrides.forEach((name, value) -> {
                merged.keySet().removeIf(existingName -> existingName.equalsIgnoreCase(name));
                merged.put(name, value);
            });
        }
        return Collections.unmodifiableMap(merged);
    }

    /**
     * Combines two {@link HttpParameters}: custom headers are merged by name (ignoring case),
     * the ones from {@code overrides} winning. Neither object is modified.
     *
     * @param httpParameters the parameters to start from; {@code null} means none
     * @param overrides      the parameters that win; {@code null} means none
     * @return the combined parameters, or {@code null} if both are {@code null}
     */
    public static @Nullable HttpParameters mergeHttpParameters(
            @Nullable HttpParameters httpParameters, @Nullable HttpParameters overrides) {
        if (httpParameters == null) {
            return overrides;
        }
        if (overrides == null) {
            return httpParameters;
        }
        return HttpParameters.builder()
                .customHeaders(mergeCustomHeaders(httpParameters.customHeaders(), overrides.customHeaders()))
                .build();
    }

    /**
     * Returns the headers that a model integration adds last to an HTTP request for the given per-request headers.
     * A per-request header replaces a model header with the same name (ignoring case), except {@code User-Agent}:
     * its value is a list of product tokens, so the per-request value is appended to the model's value,
     * separated by a space.
     *
     * @param modelHeaders   the headers the model sends anyway (its own and the builder's custom headers)
     * @param requestHeaders the per-request custom headers
     * @return the headers to add last
     */
    public static Map<String, String> perRequestHeaders(
            Map<String, String> modelHeaders, Map<String, String> requestHeaders) {
        return perRequestHeaders(modelHeaders, requestHeaders, Set.of());
    }

    /**
     * Like {@link #perRequestHeaders(Map, Map)}, but the per-request value is also appended (not replaced)
     * for the given header names, for provider-specific headers that hold a list of tokens.
     *
     * @param appendedHeaderNames header names (ignoring case) to append to, in addition to {@code User-Agent}
     */
    public static Map<String, String> perRequestHeaders(
            Map<String, String> modelHeaders, Map<String, String> requestHeaders, Set<String> appendedHeaderNames) {
        Map<String, String> result = new LinkedHashMap<>();
        requestHeaders.forEach((name, value) -> {
            String modelValue = isAppended(name, appendedHeaderNames) ? valueOf(modelHeaders, name) : null;
            result.put(name, modelValue == null ? value : modelValue + " " + value);
        });
        return Collections.unmodifiableMap(result);
    }

    private static boolean isAppended(String name, Set<String> appendedHeaderNames) {
        return USER_AGENT.equalsIgnoreCase(name) || appendedHeaderNames.stream().anyMatch(name::equalsIgnoreCase);
    }

    private static @Nullable String valueOf(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
                .filter(header -> header.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns the custom HTTP headers of the given {@link HttpParameters}, or an empty map if there are none.
     */
    public static Map<String, String> customHeadersOf(@Nullable HttpParameters httpParameters) {
        return httpParameters == null ? Map.of() : httpParameters.customHeaders();
    }

    /**
     * Returns the custom HTTP headers of {@link ChatRequestParameters#httpParameters()},
     * or an empty map if there are none.
     */
    public static Map<String, String> customHeadersOf(ChatRequestParameters parameters) {
        return customHeadersOf(parameters.httpParameters());
    }

    /**
     * Returns the exception that a client SPI default method throws (or signals) when it is asked to send
     * per-request custom HTTP headers that the given client implementation cannot send.
     */
    public static UnsupportedFeatureException perRequestCustomHeadersNotSupported(Class<?> clientType) {
        return new UnsupportedFeatureException(clientType.getName()
                + " does not support per-request custom HTTP headers (ChatRequestParameters.httpParameters())");
    }
}
