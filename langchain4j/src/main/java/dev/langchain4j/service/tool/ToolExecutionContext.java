package dev.langchain4j.service.tool;

import dev.langchain4j.InvocationContext;
import java.util.Objects;

/**
 * @since 1.4.0
 */
public class ToolExecutionContext { // TODO name, location

    private final Object chatMemoryId; // TODO name?
    private final InvocationContext invocationContext;

    public ToolExecutionContext(Object chatMemoryId, InvocationContext invocationContext) {
        this.chatMemoryId = chatMemoryId; // TODO
        this.invocationContext = invocationContext; // TODO
    }

    public Object chatMemoryId() { // TODO names
        return chatMemoryId;
    }

    public InvocationContext invocationContext() {
        return invocationContext;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ToolExecutionContext that = (ToolExecutionContext) object;
        return Objects.equals(chatMemoryId, that.chatMemoryId)
                && Objects.equals(invocationContext, that.invocationContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatMemoryId, invocationContext);
    }

    @Override
    public String toString() {
        return "ToolExecutionContext {" + // TODO names
                " chatMemoryId = " + chatMemoryId +
                " invocationContext = " + invocationContext +
                " }";
    }
}
