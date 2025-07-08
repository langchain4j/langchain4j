package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.MemoryId;

/**
 * A low-level executor/handler of a {@link ToolExecutionRequest}.
 */
public interface ToolExecutor {

    /**
     * Executes a tool requests.
     *
     * @param toolExecutionRequest The tool execution request. Contains tool name and arguments.
     * @param memoryId             The ID of the chat memory. See {@link MemoryId} for more details.
     * @return The result of the tool execution.
     */
    String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId);


    /**
     * Invokes a tool request, but does not marshal the result to a string.
     *
     * @param toolExecutionRequest The tool execution request. Contains tool name and arguments.
     * @param memoryId              The ID of the chat memory. See {@link MemoryId} for more details.
     * @throws Throwable Propagated exception from the reflection call on the tool method
     * @return The tool execution result.
     */
    default Object invoke(ToolExecutionRequest toolExecutionRequest, Object memoryId) throws Throwable {
        // Default implementation is for backward compatibility with the old ToolExecutor interface.
        throw new UnsupportedOperationException("invoke is not supported by this tool executor");
    }

}
