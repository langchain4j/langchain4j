package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;

/**
 * A low-level executor/handler of a {@link ToolExecutionRequest}.
 */
@FunctionalInterface
public interface ToolExecutor {

    /**
     * Executes a tool request.
     *
     * @param request The tool execution request. Contains tool name and arguments.
     * @param context The AI Service invocation context, including the chat memory ID and invocation parameters.
     * @return The result of the tool execution that will be sent to the LLM.
     */
    ToolExecutionResult execute(ToolExecutionRequest request, InvocationContext context);
}
