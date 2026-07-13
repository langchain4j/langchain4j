package dev.langchain4j.agentic.patterns.htn;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface DecompositionStrategy {
    List<TaskNode> decompose(AgenticScope scope, Map<Class<?>, AgentInstance> agentsByType);
}
