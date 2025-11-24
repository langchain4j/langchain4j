package dev.langchain4j.agentic.declarative;

public interface AgentState<T> {
    default T defaultValue() {
        return null;
    }

    default String name() {
        return this.getClass().getSimpleName();
    }
}
