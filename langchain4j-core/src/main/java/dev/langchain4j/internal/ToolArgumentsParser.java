package dev.langchain4j.internal;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.Internal;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leniently parses the JSON arguments of a tool execution request.
 * Shared by the tool executor and the tool guardrail requests so both interpret arguments identically.
 *
 * @since 1.24.0
 */
@Internal
public class ToolArgumentsParser {

    private static final Pattern TRAILING_COMMA_PATTERN = Pattern.compile(",(\\s*[}\\]])");
    private static final Pattern LEADING_TRAILING_QUOTE_PATTERN = Pattern.compile("^\"|\"$");
    private static final Pattern ESCAPED_QUOTE_PATTERN = Pattern.compile("\\\\\"");

    private static final Type MAP_TYPE = Types.parameterized(Map.class, String.class, Object.class);

    private ToolArgumentsParser() {}

    /**
     * Converts a JSON arguments string to a map. Null or blank input gives an empty map.
     * If the first parse fails, the input is normalized (surrounding quotes removed, escaped quotes
     * unescaped, trailing commas removed) and parsed once more; a second failure propagates.
     *
     * @param arguments json string
     * @return the parsed map (mutable; may be null if the JSON is the literal {@code null})
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
     * Removes trailing commas before closing braces or brackets in JSON strings.
     *
     * @param json the JSON string
     * @return the corrected JSON string
     */
    public static String removeTrailingComma(String json) {
        if (isNullOrEmpty(json)) {
            return json;
        }
        return TRAILING_COMMA_PATTERN.matcher(json).replaceAll("$1");
    }

    /**
     * Normalizes a JSON string by removing leading and trailing quotes and unescaping internal double quotes.
     *
     * @param arguments the raw JSON string
     * @return the normalized JSON string
     */
    public static String normalizeJsonString(String arguments) {
        if (isNullOrEmpty(arguments)) {
            return arguments;
        }

        Matcher leadingTrailingMatcher = LEADING_TRAILING_QUOTE_PATTERN.matcher(arguments);
        String normalizedJson = leadingTrailingMatcher.replaceAll("");
        return ESCAPED_QUOTE_PATTERN.matcher(normalizedJson).replaceAll("\"");
    }
}
