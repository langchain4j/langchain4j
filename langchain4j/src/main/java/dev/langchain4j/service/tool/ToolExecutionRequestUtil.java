package dev.langchain4j.service.tool;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.internal.ToolArgumentsParser;
import java.util.Map;

/**
 * Utility class for {@link ToolExecutionRequest}.
 *
 * @deprecated Use {@link ToolArgumentsParser} instead. This class only delegates to it.
 */
@Internal
@Deprecated(since = "1.24.0")
public class ToolExecutionRequestUtil {

    private ToolExecutionRequestUtil() {}

    /**
     * Convert arguments to map.
     *
     * @param arguments json string
     * @return map
     */
    public static Map<String, Object> argumentsAsMap(String arguments) {
        return ToolArgumentsParser.argumentsAsMap(arguments);
    }

    /**
     * Removes trailing commas before closing braces or brackets in JSON strings.
     *
     * @param json the JSON string
     * @return the corrected JSON string
     */
    static String removeTrailingComma(String json) {
        return ToolArgumentsParser.removeTrailingComma(json);
    }

    /**
     * Normalizes a JSON string by removing leading and trailing quotes and unescaping internal double quotes.
     *
     * @param arguments the raw JSON string
     * @return the normalized JSON string
     */
    static String normalizeJsonString(String arguments) {
        return ToolArgumentsParser.normalizeJsonString(arguments);
    }
}
