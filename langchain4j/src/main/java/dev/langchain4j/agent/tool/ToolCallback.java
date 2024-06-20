package dev.langchain4j.agent.tool;

/**
 * TODO
 *
 * @param <T>
 */
public interface ToolCallback<T> {
    // TODO name
    // TODO location

    /**
     * TODO
     *
     * @param request
     * @return
     */
    ToolResult execute(ToolRequest<T> request); // TODO names
}
