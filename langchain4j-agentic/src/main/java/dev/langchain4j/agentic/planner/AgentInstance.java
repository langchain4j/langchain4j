package dev.langchain4j.agentic.planner;

import java.util.List;

public interface AgentInstance {

    String name();

    String agentId();

    String description();

    String outputKey();

    List<AgentArgument> arguments();

    default boolean isStreaming() {
        return false;
    }

    default void setStreaming(boolean streaming) {}
}
