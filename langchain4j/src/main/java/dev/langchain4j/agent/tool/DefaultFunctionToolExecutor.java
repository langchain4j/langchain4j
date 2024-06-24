package dev.langchain4j.agent.tool;

import dev.langchain4j.internal.Json;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class DefaultFunctionToolExecutor<T> implements ToolExecutor {
    // TODO name

    private final Class<T> argumentClass;
    private final ToolCallback<T> callback;

    public DefaultFunctionToolExecutor(Class<T> argumentClass, ToolCallback<T> callback) {
        this.argumentClass = argumentClass; // TODO
        this.callback = ensureNotNull(callback, "callback");
    }

    @Override
    public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
        T argument;
        if ("{}".equals(toolExecutionRequest.arguments().trim())) { // TODO
            argument = null;
        } else {
            argument = Json.fromJson(toolExecutionRequest.arguments(), argumentClass);
        }
        ToolRequest<T> request = new ToolRequest<>(argument, memoryId);
        ToolResult result = callback.execute(request);
        return result.result();
    }
}
