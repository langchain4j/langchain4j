package dev.langchain4j.service.tool;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.internal.Json;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

/**
 * Utility class for {@link ToolExecutionRequest}.
 */
@Internal
class ToolExecutionRequestUtil {

    private static final Pattern TRAILING_COMMA_PATTERN = Pattern.compile(",(\\s*[}\\]])");
    private static final Pattern LEADING_TRAILING_QUOTE_PATTERN = Pattern.compile("^\"|\"$");
    private static final Pattern ESCAPED_QUOTE_PATTERN = Pattern.compile("\\\\\"");

    private ToolExecutionRequestUtil() {
    }

    private static final Type MAP_TYPE = new ParameterizedType() {

        @Override
        public Type[] getActualTypeArguments() {
            return new Type[]{String.class, Object.class};
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
    static Map<String, Object> argumentsAsMap(String arguments) {
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
     * Removes trailing commas before closing braces or brackets in JSON strings.
     *
     * @param json the JSON string
     * @return the corrected JSON string
     */
    static String removeTrailingComma(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        Matcher matcher = TRAILING_COMMA_PATTERN.matcher(json);
        return matcher.replaceAll("$1");
    }

    /**
     * Normalizes a JSON string by removing leading and trailing quotes and unescaping internal double quotes.
     *
     * @param arguments the raw JSON string
     * @return the normalized JSON string
     */
    static String normalizeJsonString(String arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return arguments;
        }

        Matcher leadingTrailingMatcher = LEADING_TRAILING_QUOTE_PATTERN.matcher(arguments);
        String normalizedJson = leadingTrailingMatcher.replaceAll("");

        Matcher escapedQuoteMatcher = ESCAPED_QUOTE_PATTERN.matcher(normalizedJson);
        return escapedQuoteMatcher.replaceAll("\"");
    }

}
