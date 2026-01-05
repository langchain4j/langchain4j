package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.planner.AgentInstance;

public interface InternalAgent extends AgentInstance {

    void setParent(InternalAgent parent);

    void appendId(String idSuffix);

    default boolean allowStreamingOutput() {
        throw new UnsupportedOperationException();
    }
}
