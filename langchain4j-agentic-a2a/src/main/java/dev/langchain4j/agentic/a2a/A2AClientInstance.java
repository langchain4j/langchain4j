package dev.langchain4j.agentic.a2a;

import dev.langchain4j.agentic.internal.InternalAgent;
import io.a2a.spec.AgentCard;

public interface A2AClientInstance extends InternalAgent {

    String[] inputKeys();

    AgentCard agentCard();
}
