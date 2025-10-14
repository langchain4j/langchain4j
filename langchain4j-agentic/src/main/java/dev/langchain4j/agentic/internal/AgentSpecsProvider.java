package dev.langchain4j.agentic.internal;

public interface AgentSpecsProvider {

    String inputKey();

    String outputKey();

    String description();

    boolean async();
}
