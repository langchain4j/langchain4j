package dev.langchain4j.agentic.scope;

public interface AgentExecutionListener {

    AgentExecutionListener NO_OP = agentInvocation -> { };

    void onAgentInvoked(AgentInvocation agentInvocation);
}
