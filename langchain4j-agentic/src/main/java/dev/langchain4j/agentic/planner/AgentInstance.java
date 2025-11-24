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

    List<AgentArgument> arguments();

    List<AgentInstance> subagents();
}
