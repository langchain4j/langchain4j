package dev.langchain4j.agentic.planner;

import java.util.List;

public class DefaultAgentInstance implements AgentInstance {
    private String name;
    private String agentId;
    private String description;
    private String outputKey;
    private List<AgentArgument> arguments;
    private boolean streaming;

    public DefaultAgentInstance(
            final String name,
            final String agentId,
            final String description,
            final String outputKey,
            final List<AgentArgument> arguments) {
        this.name = name;
        this.agentId = agentId;
        this.description = description;
        this.outputKey = outputKey;
        this.arguments = arguments;
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
    public String outputKey() {
        return this.outputKey;
    }

    @Override
    public List<AgentArgument> arguments() {
        return this.arguments;
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
