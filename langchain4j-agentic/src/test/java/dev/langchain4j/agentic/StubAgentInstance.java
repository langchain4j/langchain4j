package dev.langchain4j.agentic;

import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Minimal {@link AgentInstance} stub for unit testing.
 * Only {@link #arguments()} and {@link #name()} are meaningful; other methods return defaults.
 */
class StubAgentInstance implements AgentInstance {

    private final String name;
    private final List<AgentArgument> arguments;

    StubAgentInstance(String name, List<AgentArgument> arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<AgentArgument> arguments() {
        return arguments;
    }

    @Override
    public String agentId() {
        return name;
    }

    @Override
    public String description() {
        return "";
    }

    @Override
    public Class<?> type() {
        return Object.class;
    }

    @Override
    public Class<? extends Planner> plannerType() {
        return null;
    }

    @Override
    public Type outputType() {
        return String.class;
    }

    @Override
    public String outputKey() {
        return null;
    }

    @Override
    public boolean async() {
        return false;
    }

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
        return AgenticSystemTopology.AI_AGENT;
    }
}
