package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Exceptions.runtime;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.function.Supplier;

public interface HallucinatedToolExecutorFactory extends Supplier<HallucinatedToolExecutor> {

    HallucinatedToolExecutor FAILURE = (ToolExecutionRequest toolExecutionRequest, Object memoryId) -> {
        throw runtime(
                "Something is wrong, the tool %s was called but it is not a part of the available tools",
                toolExecutionRequest.name());
    };
    HallucinatedToolExecutor UNKNOWN = (ToolExecutionRequest toolExecutionRequest, Object memoryId) ->
            "Unknown function \"%s\"".formatted(toolExecutionRequest.name());

    HallucinatedToolExecutorFactory DEFAULT = new HallucinatedToolExecutorFactory() {
        @Override
        public HallucinatedToolExecutor get() {
            return FAILURE;
        }
    };
}
