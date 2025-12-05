package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.AgentRequest;
import dev.langchain4j.agentic.agent.AgentResponse;
import dev.langchain4j.agentic.planner.AgentInstance;

public interface AgentSpecification extends AgentInstance {

    boolean async();

    void beforeInvocation(AgentRequest request);

    void afterInvocation(AgentResponse response);
}
