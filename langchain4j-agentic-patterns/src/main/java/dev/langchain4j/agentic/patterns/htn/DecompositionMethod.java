package dev.langchain4j.agentic.patterns.htn;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public record DecompositionMethod(Predicate<AgenticScope> guard, DecompositionStrategy strategy) {

    List<TaskNode> decompose(AgenticScope scope, Map<Class<?>, AgentInstance> agentsByType) {
        return strategy.decompose(scope, agentsByType);
    }

    public static DecompositionMethod decompose(Predicate<AgenticScope> guard, TaskNode... subtasks) {
        return decompose(guard, List.of(subtasks));
    }

    public static DecompositionMethod decompose(Predicate<AgenticScope> guard, List<TaskNode> subtasks) {
        return new DecompositionMethod(guard, (scope, agents) -> subtasks);
    }

    public static DecompositionMethod decompose(TaskNode... subtasks) {
        return decompose(List.of(subtasks));
    }

    public static DecompositionMethod decompose(List<TaskNode> subtasks) {
        return new DecompositionMethod(s -> true, (scope, agents) -> subtasks);
    }
}
