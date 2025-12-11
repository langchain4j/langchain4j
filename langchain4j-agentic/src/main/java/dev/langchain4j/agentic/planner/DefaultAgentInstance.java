package dev.langchain4j.agentic.planner;

import java.lang.reflect.Type;
import java.util.List;

public record DefaultAgentInstance(Class<?> type, String name, String agentId, String description, Type outputType, String outputKey,
                                   List<AgentArgument> arguments, List<AgentInstance> subagents, AgenticSystemTopology topology)
        implements AgentInstance {
    
    @Override
    public boolean async() {
        return false;
    }
}
