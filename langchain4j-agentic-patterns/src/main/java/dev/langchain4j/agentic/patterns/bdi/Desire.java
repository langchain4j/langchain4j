package dev.langchain4j.agentic.patterns.bdi;

import dev.langchain4j.agentic.scope.AgenticScope;

import java.util.List;
import java.util.function.Predicate;

public record Desire(String name, int priority,
                     Predicate<AgenticScope> achievable,
                     Predicate<AgenticScope> satisfied,
                     List<Class<?>> agentTypes) {

    public static Desire of(String name, int priority,
                            Predicate<AgenticScope> achievable,
                            Predicate<AgenticScope> satisfied,
                            Class<?>... agentTypes) {
        return new Desire(name, priority, achievable, satisfied, List.of(agentTypes));
    }

    public static Desire of(String name, int priority,
                            String achievableStateKey,
                            String satisfiedStateKey,
                            Class<?>... agentTypes) {
        return new Desire(name, priority,
                scope -> scope.hasState(achievableStateKey),
                scope -> scope.hasState(satisfiedStateKey),
                List.of(agentTypes));
    }
}
