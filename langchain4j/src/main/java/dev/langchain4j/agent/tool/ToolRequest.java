package dev.langchain4j.agent.tool;

/**
 * TODO
 * @param <T>
 */
public class ToolRequest<T> {

    private final T argument;

    public ToolRequest(T argument) {
        this.argument = argument;
    }

    public T argument() {
        return argument;
    }
}
