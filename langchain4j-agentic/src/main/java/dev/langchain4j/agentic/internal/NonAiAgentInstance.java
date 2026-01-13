package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

public class NonAiAgentInstance implements AgentInstance, InternalAgent {
    private final Class<?> type;
    private final String name;
    private final String description;
    private final Type outputType;
    private final String outputKey;
    private final boolean async;
    private final List<AgentArgument> arguments;
    private final AgentListener listener;

    private InternalAgent parent;
    private String agentId;

    public NonAiAgentInstance(Class<?> type, String name, String description,
            Type outputType, String outputKey, boolean async, List<AgentArgument> arguments,
            AgentListener listener) {
        this.type = type;
        this.name = name;
        this.agentId = name;
        this.description = description;
        this.outputType = outputType;
        this.outputKey = outputKey;
        this.async = async;
        this.arguments = arguments;
        this.listener = listener;
    }

    @Override
    public String agentId() {
        return agentId;
    }

    @Override
    public AgentInstance parent() {
        return parent;
    }

    @Override
    public List<AgentInstance> subagents() {
        return List.of();
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.SINGLE_AGENT;
    }

    @Override
    public Class<?> type() {
        return type;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public Type outputType() {
        return outputType;
    }

    @Override
    public String outputKey() {
        return outputKey;
    }

    @Override
    public boolean async() {
        return async;
    }

    @Override
    public List<AgentArgument> arguments() {
        return arguments;
    }

    @Override
    public AgentListener listener() {
        return listener;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (NonAiAgentInstance) obj;
        return Objects.equals(this.type, that.type) &&
                Objects.equals(this.name, that.name) &&
                Objects.equals(this.description, that.description) &&
                Objects.equals(this.outputType, that.outputType) &&
                Objects.equals(this.outputKey, that.outputKey) &&
                this.async == that.async &&
                Objects.equals(this.arguments, that.arguments) &&
                Objects.equals(this.listener, that.listener);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name, description, outputType, outputKey, async, arguments, listener);
    }

    @Override
    public String toString() {
        return "NonAiAgentInstance[" +
                "type=" + type + ", " +
                "name=" + name + ", " +
                "description=" + description + ", " +
                "outputType=" + outputType + ", " +
                "outputKey=" + outputKey + ", " +
                "async=" + async + ", " +
                "arguments=" + arguments + ", " +
                "listener=" + listener + ']';
    }

    @Override
    public void setParent(InternalAgent parent) {
        this.parent = parent;
    }

    @Override
    public void appendId(String idSuffix) {
        this.agentId = this.agentId + idSuffix;
    }
}
