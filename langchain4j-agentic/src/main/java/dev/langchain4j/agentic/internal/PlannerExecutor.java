package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.scope.AgentInvocation;

public interface PlannerExecutor {

    void onSubagentInvoked(AgentInvocation agentInvocation);

    boolean propagateStreaming();
}
