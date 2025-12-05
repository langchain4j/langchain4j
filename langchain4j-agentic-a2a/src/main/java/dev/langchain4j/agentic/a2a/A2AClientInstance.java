package dev.langchain4j.agentic.a2a;

import dev.langchain4j.agentic.planner.AgentInstance;
import io.a2a.spec.AgentCard;

public interface A2AClientInstance extends AgentInstance {

    String[] inputKeys();

    AgentCard agentCard();
}
