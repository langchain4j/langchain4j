package dev.langchain4j.agent.tool;

import java.util.function.Function;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class DefaultFunctionToolExecutor implements ToolExecutor {

    private final Function<?, ?> function;

    public DefaultFunctionToolExecutor(Function<?, ?> function) {
        this.function = ensureNotNull(function, "function");
    }

    @Override
    public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
        // TODO how to propagate memoryId into the function?

        return function.apply(null).toString(); // TODO
    }
}
