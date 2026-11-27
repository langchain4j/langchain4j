package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.internal.Json;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for {@link ToolExecutionRequest}.
 */
@Internal
public class ToolExecutionRequestUtil {

    private static final Pattern LEADING_TRAILING_QUOTE_PATTERN = Pattern.compile("^\"|\"$");
    private static final Pattern ESCAPED_QUOTE_PATTERN = Pattern.compile("\\\\\"");

    private ToolExecutionRequestUtil() {}

    private static final Type MAP_TYPE = new ParameterizedType() {

        @Override
        public Type[] getActualTypeArguments() {
            return new Type[] {String.class, Object.class};
        }

        @Override
        public Type getRawType() {
            return Map.class;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    };

    /**
     * Convert arguments to map.
     *
     * @param arguments json string
     * @return map
     */
    public static Map<String, Object> argumentsAsMap(String arguments) {
        if (isNullOrBlank(arguments)) {
            return Map.of();
        }

        try {
            return Json.fromJson(arguments, MAP_TYPE);
        } catch (Exception ignored) {
            try {
                // Repair structural trailing commas before touching escapes in string values.
                return Json.fromJson(removeTrailingComma(arguments), MAP_TYPE);
            } catch (Exception ignoredAgain) {
                String normalizedArguments = removeTrailingComma(normalizeJsonString(arguments));
                return Json.fromJson(normalizedArguments, MAP_TYPE);
            }
        }
    }

    /**
     * Removes trailing commas before closing braces or brackets in JSON strings.
     * Commas inside quoted string values and their escape sequences are preserved.
     *
     * @param json the JSON string
     * @return the corrected JSON string
     */
    static String removeTrailingComma(String json) {
        if (isNullOrEmpty(json)) {
            return json;
        }

        StringBuilder result = new StringBuilder(json.length());
        boolean insideString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (insideString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    insideString = false;
                }
            } else if (c == '"') {
                insideString = true;
            } else if (c == ',' && isFollowedByClosingBraceOrBracket(json, i + 1)) {
                continue; // skip the trailing comma
            }
            result.append(c);
        }

        return result.toString();
    }

    private static boolean isFollowedByClosingBraceOrBracket(String json, int fromIndex) {
        for (int i = fromIndex; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '}' || c == ']') {
                return true;
            }
            if (!isWhitespace(c)) {
                return false;
            }
        }
        return false;
    }

    private static boolean isWhitespace(char c) {
        // same characters as the "\s" regex character class
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' || c == 0x0B;
    }

    /**
     * Normalizes a JSON string by removing leading and trailing quotes and unescaping internal double quotes.
     *
     * @param arguments the raw JSON string
     * @return the normalized JSON string
     */
    static String normalizeJsonString(String arguments) {
        if (isNullOrEmpty(arguments)) {
            return arguments;
        }

        Matcher leadingTrailingMatcher = LEADING_TRAILING_QUOTE_PATTERN.matcher(arguments);
        String normalizedJson = leadingTrailingMatcher.replaceAll("");

        Matcher escapedQuoteMatcher = ESCAPED_QUOTE_PATTERN.matcher(normalizedJson);
        return escapedQuoteMatcher.replaceAll("\"");
    }
}
