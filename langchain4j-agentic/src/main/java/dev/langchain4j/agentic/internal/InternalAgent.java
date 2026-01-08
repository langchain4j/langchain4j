package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentInstance;

public interface InternalAgent extends AgentInstance {

    void setParent(InternalAgent parent);

    void appendId(String idSuffix);

    AgentListener listener();

    default boolean allowStreamingOutput() {
        throw new UnsupportedOperationException();
    }
}
