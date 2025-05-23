package dev.langchain4j.agentic.internal;

import io.a2a.spec.AgentCard;

public interface A2AClientInstance extends AgentInstance {

    String[] inputNames();

    AgentCard agentCard();
}
