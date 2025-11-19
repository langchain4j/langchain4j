package dev.langchain4j.agentic.planner;

import java.util.List;

public record DefaultAgentInstance(String name, String agentId, String description, String outputKey, List<AgentArgument> arguments)
        implements AgentInstance {
}
