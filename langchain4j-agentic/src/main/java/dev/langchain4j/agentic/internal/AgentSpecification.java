package dev.langchain4j.agentic.internal;

public interface AgentSpecification {

    String name();
    String description();
    String outputName();
    boolean async();
}
