package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.observability.AgentListener;

public interface AgentSpecsProvider {

    String outputKey();

    String description();

    boolean async();

    AgentListener listener();
}
