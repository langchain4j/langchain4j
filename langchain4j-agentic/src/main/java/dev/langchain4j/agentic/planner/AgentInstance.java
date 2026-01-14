package dev.langchain4j.agentic.planner;

import java.lang.reflect.Type;
import java.util.List;

public interface AgentInstance {

    Class<?> type();

    String name();

    String agentId();

    String description();

    Type outputType();

    String outputKey();

    boolean async();

    List<AgentArgument> arguments();

    AgentInstance parent();
    List<AgentInstance> subagents();

    default boolean leaf() {
        return subagents().isEmpty();
    }

    AgenticSystemTopology topology();

    default <T extends AgentInstance> T as(Class<T> agentInstanceClass) {
        throw new ClassCastException("Cannot cast to " + agentInstanceClass.getName() + ": incompatible type");
    }
}
