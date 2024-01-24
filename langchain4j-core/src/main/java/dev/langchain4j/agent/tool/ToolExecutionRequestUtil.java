package dev.langchain4j.agent.tool;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Utility class for {@link ToolExecutionRequest}.
 */
public class ToolExecutionRequestUtil {
    private ToolExecutionRequestUtil() {}

    /**
     * Gson instance.
     */
    public static final Gson GSON = new Gson();

    /**
     * Utility {@link TypeToken} describing {@code Map<String, Object>}.
     */
    public static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    /**
     * Get an argument value from ToolExecutionRequest.
     * @param toolExecutionRequest request
     * @param name argument name
     * @return argument value
     * @param <T> the argument type
     */
    public static <T> T argument(ToolExecutionRequest toolExecutionRequest, String name) {
        Map<String, Object> arguments = argumentsAsMap(toolExecutionRequest.arguments()); // TODO cache
        @SuppressWarnings("unchecked")
        T res = (T) arguments.get(name);
        return res;
    }

    /**
     * Convert arguments to map.
     * @param arguments json string
     * @return map
     */
    public static Map<String, Object> argumentsAsMap(String arguments) {
        return GSON.fromJson(arguments, MAP_TYPE);
    }
}
