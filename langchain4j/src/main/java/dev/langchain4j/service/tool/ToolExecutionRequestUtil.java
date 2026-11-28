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
@Deprecated(since = "1.25.0")
public class ToolExecutionRequestUtil {

    private ToolExecutionRequestUtil() {}

    /**
     * Convert arguments to map.
     *
     * @param arguments json string
     * @return map
     * @deprecated Use {@link ToolArgumentsParser#argumentsAsMap(String)} instead.
     */
    @Deprecated(since = "1.25.0")
    public static Map<String, Object> argumentsAsMap(String arguments) {
        return ToolArgumentsParser.argumentsAsMap(arguments);
    }
}
