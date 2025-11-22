package dev.langchain4j.agentic.planner;

import java.lang.reflect.Type;
import java.util.List;

public class DefaultAgentInstance implements AgentInstance {
    private Class<?> type;
    private String name;
    private String agentId;
    private String description;
    private Type outputType;
    private String outputKey;
    private List<AgentArgument> arguments;
    private List<AgentInstance> subagents;
    private boolean streaming;

    public DefaultAgentInstance(
            final Class<?> type,
            final String name,
            final String agentId,
            final String description,
            final Type outputType,
            final String outputKey,
            final List<AgentArgument> arguments,
            final List<AgentInstance> subagents) {
        this.type = type;
        this.name = name;
        this.agentId = agentId;
        this.description = description;
        this.outputType = outputType;
        this.outputKey = outputKey;
        this.arguments = arguments;
        this.subagents = subagents;
    }

    @Override
    public Class<?> type() {
        return this.type;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public String agentId() {
        return this.agentId;
    }

    @Override
    public String description() {
        return this.description;
    }

    @Override
    public Type outputType() {
        return this.outputType;
    }

    @Override
    public String outputKey() {
        return this.outputKey;
    }

    @Override
    public List<AgentArgument> arguments() {
        return this.arguments;
    }

    @Override
    public List<AgentInstance> subagents() {
        return subagents;
    }

    @Override
    public boolean isStreaming() {
        return streaming;
    }

    @Override
    public void setStreaming(final boolean streaming) {
        this.streaming = streaming;
    }
}
