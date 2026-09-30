package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentInstance;

public interface InternalAgent extends AgentInstance {

    void setParent(InternalAgent parent);

    void registerInheritedParentListener(AgentListener parentListener);

    void appendId(String idSuffix);

    default void setAgentId(String agentId) {}

    AgentListener listener();

    default boolean allowStreamingOutput() {
        throw new UnsupportedOperationException();
    }

    default boolean allowChatMemory() {
        return true;
    }

    default boolean compensateOnError() {
        return false;
    }

    default void enableCrossAgentCompensation() {
    }
}
