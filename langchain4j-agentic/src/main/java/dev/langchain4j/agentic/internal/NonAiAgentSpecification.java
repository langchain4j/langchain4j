package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.AgentRequest;
import dev.langchain4j.agentic.agent.AgentResponse;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;

public record NonAiAgentSpecification(
        Class<?> type,
        String name,
        String agentId,
        String description,
        Type outputType,
        String outputKey,
        boolean async,
        List<AgentArgument> arguments,
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

    @Override
    public List<AgentInstance> subagents() {
        return List.of();
    }
}
