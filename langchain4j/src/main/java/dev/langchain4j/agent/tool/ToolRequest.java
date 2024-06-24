package dev.langchain4j.agent.tool;

import dev.langchain4j.Experimental;

/**
 * TODO
 *
 * @param <T>
 */
@Experimental
public class ToolRequest<T> {

    private final T argument;
    private final Object memoryId;

    public ToolRequest(T argument, Object memoryId) {
        this.argument = argument;
        this.memoryId = memoryId;
    }

    public T argument() {
        return argument;
    }

    public Object memoryId() {
        return memoryId;
    }
}
