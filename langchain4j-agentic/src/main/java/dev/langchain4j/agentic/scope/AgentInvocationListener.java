package dev.langchain4j.agentic.scope;

public interface AgentInvocationListener {

    AgentInvocationListener NO_OP = agentInvocation -> {};

    void onAgentInvoked(AgentInvocation agentInvocation);
}
