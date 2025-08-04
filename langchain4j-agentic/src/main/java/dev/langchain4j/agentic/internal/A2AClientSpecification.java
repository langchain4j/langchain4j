package dev.langchain4j.agentic.internal;

import io.a2a.spec.AgentCard;

public interface A2AClientSpecification extends AgentSpecification {

    String[] inputNames();

    AgentCard agentCard();
}
