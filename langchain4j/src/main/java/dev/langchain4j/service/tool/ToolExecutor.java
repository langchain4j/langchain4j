package dev.langchain4j.service.tool;

import dev.langchain4j.InvocationContext;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.MemoryId;

/**
 * A low-level executor/handler of a {@link ToolExecutionRequest}.
 */
@FunctionalInterface
public interface ToolExecutor {

    /**
     * TODO
     *
     * @param request TODO
     * @param context TODO
     * @return TODO
     */
    default ToolExecutionResult execute(ToolExecutionRequest request, InvocationContext context) {
        String result = execute(request, context.chatMemoryId());
        return ToolExecutionResult.builder()
                .resultText(result)
                .build();
    }

    /**
     * Executes a tool requests.
     *
     * @param request  The tool execution request. Contains tool name and arguments.
     * @param memoryId The ID of the chat memory. See {@link MemoryId} for more details.
     * @return The result of the tool execution that will be sent to the LLM.
     */
    // TODO deprecate?
    String execute(ToolExecutionRequest request, Object memoryId);
}
