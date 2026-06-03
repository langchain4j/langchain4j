package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;

import java.util.List;

public interface AgentSpecsProvider {

    String outputKey();

    String description();

    boolean async();

    AgentListener listener();

    default List<AgentArgument> arguments() {
        return null;
    }
}
