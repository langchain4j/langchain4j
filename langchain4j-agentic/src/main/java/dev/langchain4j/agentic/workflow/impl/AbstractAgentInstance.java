package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import java.lang.reflect.Type;
import java.util.List;

abstract class AbstractAgentInstance implements AgentInstance {
    private final AgentInstance delegate;

    AbstractAgentInstance(final AgentInstance delegate) {
        this.delegate = delegate;
    }

    @Override
    public Class<?> type() {
        return delegate.type();
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public String agentId() {
        return delegate.agentId();
    }

    @Override
    public String description() {
        return delegate.description();
    }

    @Override
    public Type outputType() {
        return delegate.outputType();
    }

    @Override
    public String outputKey() {
        return delegate.outputKey();
    }

    @Override
    public boolean async() {
        return delegate.async();
    }

    @Override
    public List<AgentArgument> arguments() {
        return delegate.arguments();
    }

    @Override
    public AgentInstance parent() {
        return delegate.parent();
    }

    @Override
    public List<AgentInstance> subagents() {
        return delegate.subagents();
    }

    @Override
    public boolean leaf() {
        return delegate.leaf();
    }

    @Override
    public AgenticSystemTopology topology() {
        return delegate.topology();
    }

    @Override
    public <T extends AgentInstance> T as(final Class<T> agentInstanceClass) {
        return delegate.as(agentInstanceClass);
    }
}
