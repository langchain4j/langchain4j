package dev.langchain4j.agentic.internal;

public record AgentSpecificationImpl(String name, String uniqueName, String description, String outputName, boolean async)
        implements AgentSpecification {
}
