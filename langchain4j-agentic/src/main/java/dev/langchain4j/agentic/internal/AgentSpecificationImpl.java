package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.AgentRequest;
import dev.langchain4j.agentic.agent.AgentResponse;
import java.util.function.Consumer;

public record AgentSpecificationImpl(
        String name,
        String uniqueName,
        String description,
        String outputKey,
        boolean async,
        Consumer<AgentRequest> invocationListener,
        Consumer<AgentResponse> completionListener)
        implements AgentSpecification {

    @Override
    public void beforeInvocation(AgentRequest request) {
        invocationListener.accept(request);
    }

    @Override
    public void afterInvocation(AgentResponse response) {
        completionListener.accept(response);
    }
}
