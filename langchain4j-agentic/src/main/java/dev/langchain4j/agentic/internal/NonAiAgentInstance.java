package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentListenerProvider;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;

public record NonAiAgentInstance(
        Class<?> type,
        String name,
        String agentId,
        String description,
        Type outputType,
        String outputKey,
        boolean async,
        List<AgentArgument> arguments,
        Consumer<AgentRequest> invocationListener,
        Consumer<AgentResponse> completionListener,
        AgentListener listener)
        implements AgentInstance, AgentListenerProvider {

    @Override
    public AgentInstance parent() {
        return null;
    }

    @Override
    public List<AgentInstance> subagents() {
        return List.of();
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.SINGLE_AGENT;
    }
}
