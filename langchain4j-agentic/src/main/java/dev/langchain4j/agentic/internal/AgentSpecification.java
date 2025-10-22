package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.AgentRequest;
import dev.langchain4j.agentic.agent.AgentResponse;

public interface AgentSpecification {

    String name();

    String uniqueName();

    String description();

    String outputKey();

    boolean async();

    void beforeInvocation(AgentRequest request);

    void afterInvocation(AgentResponse response);
}
