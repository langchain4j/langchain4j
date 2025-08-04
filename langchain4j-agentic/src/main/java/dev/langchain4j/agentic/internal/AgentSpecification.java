package dev.langchain4j.agentic.internal;

public interface AgentSpecification {

    enum Type {
        LOCAL,
        A2A
    }

    String outputName();
}
