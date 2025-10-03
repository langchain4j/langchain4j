package dev.langchain4j.agentic.scope;

public interface AgentExecutionListener {

    AgentExecutionListener NO_OP = agentExecution -> { };

    void onAgentExecuted(AgentExecution agentExecution);
}
