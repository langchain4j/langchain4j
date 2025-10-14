package dev.langchain4j.agentic.internal;

public interface AgentSpecsProvider {

    String inputName();
    String outputKey();
    String description();
    boolean async();
}
