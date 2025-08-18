package dev.langchain4j.agentic.a2a;

import dev.langchain4j.agentic.internal.AgentSpecification;
import io.a2a.spec.AgentCard;

public interface A2AClientSpecification extends AgentSpecification {

    String[] inputNames();

    AgentCard agentCard();
}
