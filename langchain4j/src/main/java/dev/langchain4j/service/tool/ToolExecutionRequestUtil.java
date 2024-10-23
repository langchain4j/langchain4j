package dev.langchain4j.service.tool;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for {@link ToolExecutionRequest}.
 */
class ToolExecutionRequestUtil {

    private static final Pattern TRAILING_COMMA_PATTERN = Pattern.compile(",(\\s*[}\\]])");

    private ToolExecutionRequestUtil() {
    }

    /**
     * Gson instance.
     */
    private static final Gson GSON = new Gson();

    /**
     * Utility {@link TypeToken} describing {@code Map<String, Object>}.
     */
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    /**
     * Convert arguments to map.
     *
     * @param arguments json string
     * @return map
     */
    static Map<String, Object> argumentsAsMap(String arguments) {
        return GSON.fromJson(removeTrailingComma(arguments), MAP_TYPE);
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
}
