package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.scope.AgentInvocation;

public interface AgentInvocationListener {

    void onAgentInvoked(AgentInvocation agentInvocation);
}
