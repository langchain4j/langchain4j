package dev.langchain4j.http.client.log;

import static dev.langchain4j.internal.SecretMasking.isSecretName;
import static dev.langchain4j.internal.SecretMasking.mask;
import static dev.langchain4j.internal.SecretMasking.maskUserInfo;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.Internal;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.internal.SecretMasking;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Internal
class HttpRequestLogger {

    private static final Pattern QUERY_PARAMETER = Pattern.compile("([?&])([^=&#]*)=([^&#]*)");

    /**
     * Query parameters that carry a credential although their name does not say so: Azure shared access signatures
     * ({@code sig}), Azure Functions keys and OAuth authorization codes ({@code code}).
     */
    private static final Set<String> OTHER_SECRET_QUERY_PARAMETERS = Set.of("sig", "code");

    private static final String SECRET_BODY_FIELD_NAMES =
            "api_key|apikey|api-key|password|secret|client_secret|token|access_token|refresh_token|id_token";

    private static final Pattern SECRET_JSON_FIELD =
            Pattern.compile("\"(?:" + SECRET_BODY_FIELD_NAMES + ")\"\\s*:\\s*\"", CASE_INSENSITIVE);

    private static final Pattern SECRET_FORM_FIELD =
            Pattern.compile("((?:^|&)(?:" + SECRET_BODY_FIELD_NAMES + ")=)([^&]*)", CASE_INSENSITIVE);

    private static final Pattern JSON_START = Pattern.compile("\\s*[\\[{]");

    static void log(Logger log, HttpRequest httpRequest) {
        try {
            log.info("""
                            HTTP request:
                            - method: {}
                            - url: {}
                            - headers: {}
                            - body: {}
                            """,
                    httpRequest.method(),
                    maskUrl(httpRequest.url()),
                    format(httpRequest.headers()),
                    maskBody(httpRequest.body()));
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
        if (isSecretName(headerKey)) {
            headerValues = headerValues.stream().map(SecretMasking::mask).collect(toList());
        }

        if (headerValues.size() == 1) {
            return String.format("[%s: %s]", headerKey, headerValues.get(0));
        } else {
            return String.format("[%s: %s]", headerKey, headerValues);
        }
    }

    static String maskUrl(String url) {
        return QUERY_PARAMETER.matcher(maskUserInfo(url)).replaceAll(match -> {
            String name = match.group(2);
            String value = isSecretQueryParameter(name) ? mask(match.group(3)) : match.group(3);
            return Matcher.quoteReplacement(match.group(1) + name + "=" + value);
        });
    }

    private static boolean isSecretQueryParameter(String name) {
        return isSecretName(name) || OTHER_SECRET_QUERY_PARAMETERS.contains(name.toLowerCase(Locale.ROOT));
    }

    static @Nullable String maskBody(@Nullable String body) {
        if (isNullOrEmpty(body)) {
            return body;
        }
        String masked = maskJsonFields(body);
        return looksLikeJson(masked) ? masked : maskFormFields(masked);
    }

    private static boolean looksLikeJson(String body) {
        return JSON_START.matcher(body).lookingAt();
    }

    private static String maskFormFields(String form) {
        return SECRET_FORM_FIELD
                .matcher(form)
                .replaceAll(match -> Matcher.quoteReplacement(match.group(1) + mask(match.group(2))));
    }

    private static String maskJsonFields(String json) {
        Matcher matcher = SECRET_JSON_FIELD.matcher(json);
        StringBuilder masked = new StringBuilder(json.length());
        int copiedUpTo = 0;
        while (matcher.find()) {
            int valueStart = matcher.end();
            int valueEnd = closingQuoteIndex(json, valueStart);
            masked.append(json, copiedUpTo, valueStart).append(mask(json.substring(valueStart, valueEnd)));
            copiedUpTo = valueEnd;
            matcher.region(valueEnd, json.length());
        }
        return masked.append(json, copiedUpTo, json.length()).toString();
    }

    /**
     * Returns the index of the quote that ends the JSON string starting at {@code from},
     * or the end of the text if the string is not terminated (e.g. the body was cut off).
     */
    private static int closingQuoteIndex(String json, int from) {
        for (int i = from; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '"') {
                return i;
            }
        }
        return json.length();
    }
}
