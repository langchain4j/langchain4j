package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.internal.Json;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utility class for {@link ToolExecutionRequest}.
 */
@Internal
public class ToolExecutionRequestUtil {

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
            String normalizedArguments = removeTrailingComma(normalizeJsonString(arguments));
            return Json.fromJson(normalizedArguments, MAP_TYPE);
        }
    }

    /**
     * Removes trailing commas before closing braces or brackets outside of JSON string literals.
     *
     * @param json the JSON string
     * @return the corrected JSON string
     */
    static String removeTrailingComma(String json) {
        if (isNullOrEmpty(json)) {
            return json;
        }

        StringBuilder result = new StringBuilder(json.length());
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                result.append(c);
                if (c == '\\' && i + 1 < json.length()) {
                    result.append(json.charAt(++i));
                } else if (c == '"') {
                    inString = false;
                }
            } else {
                if (c == '"') {
                    inString = true;
                    result.append(c);
                } else if (c == ',' && isTrailingComma(json, i)) {
                    continue;
                } else {
                    result.append(c);
                }
            }
        }
        return result.toString();
    }

    private static boolean isTrailingComma(String json, int commaIndex) {
        for (int i = commaIndex + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            return c == '}' || c == ']';
        }
        return false;
    }

    /**
     * Normalizes a quote-wrapped JSON string by removing leading and trailing quotes and unescaping internal double quotes.
     *
     * @param arguments the raw JSON string
     * @return the normalized JSON string
     */
    static String normalizeJsonString(String arguments) {
        if (isNullOrEmpty(arguments)) {
            return arguments;
        }

        if (arguments.length() >= 2 && arguments.startsWith("\"") && arguments.endsWith("\"")) {
            String unwrapped = arguments.substring(1, arguments.length() - 1);
            return ESCAPED_QUOTE_PATTERN.matcher(unwrapped).replaceAll("\"");
        }

        return arguments;
    }
}
