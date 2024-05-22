package dev.langchain4j.agent.tool;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;

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
    public static final Type MAP_TYPE = new TypeToken<Map<String, JsonElement>>() {
    }.getType();

    /**
     * Get an argument value from ToolExecutionRequest.
     * @param toolExecutionRequest request
     * @param name argument name
     * @return argument value
     * @param <T> the argument type
     */
    public static <T> T argument(ToolExecutionRequest toolExecutionRequest, String name) {
        return argument(toolExecutionRequest, name, null);
    }

    /**
     * Get an argument value from ToolExecutionRequest.
     * @param toolExecutionRequest request
     * @param name argument name
     * @param type argument type
     * @return argument value
     * @param <T> the argument type
     */
    public static <T> T argument(ToolExecutionRequest toolExecutionRequest, String name, Type type) {
        Map<String, JsonElement> arguments = argumentsAsMap(toolExecutionRequest.arguments()); // TODO cache
        @SuppressWarnings("unchecked")
        T res = (T) Optional.ofNullable(arguments.get(name))
                .map(item -> convert(item, type))
                .orElse(null);
        return res;
    }

    /**
     * Convert arguments to map.
     * @param arguments json string
     * @return map
     */
    public static Map<String, JsonElement> argumentsAsMap(String arguments) {
        return GSON.fromJson(arguments, MAP_TYPE);
    }

    /**
     * Convert jsonElement to type.
     * @param jsonElement jsonElement
     * @param type argument type
     * @return object
     */
    public static <T> T convert(JsonElement jsonElement, Type type) {
        return GSON.fromJson(jsonElement, null == type ? Object.class : type);
    }
}
