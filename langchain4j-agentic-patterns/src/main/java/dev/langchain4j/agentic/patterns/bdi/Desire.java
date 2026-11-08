package dev.langchain4j.agentic.patterns.bdi;

import dev.langchain4j.agentic.scope.AgenticScope;

import java.util.List;
import java.util.function.Predicate;

/**
 * A prioritized goal for the {@link BDIPlanner}. Each desire declares when it is achievable, when
 * it is satisfied, and the ordered sequence of agent types that form its intention. Higher priority
 * values take precedence; among equal priorities, declaration order wins (stable ordering).
 *
 * @param name       human-readable label, used in log messages and error diagnostics
 * @param priority   higher value = more important; strictly higher priority triggers preemption
 * @param achievable predicate on {@link dev.langchain4j.agentic.scope.AgenticScope} — can this desire be pursued now?
 * @param satisfied  predicate on {@link dev.langchain4j.agentic.scope.AgenticScope} — has this desire been achieved?
 * @param agentTypes ordered agent classes forming the intention; resolved to instances at init time
 */
public record Desire(String name, int priority,
                     Predicate<AgenticScope> achievable,
                     Predicate<AgenticScope> satisfied,
                     List<Class<?>> agentTypes) {

    public Desire {
        if (agentTypes == null || agentTypes.isEmpty()) {
            throw new IllegalArgumentException("Desire '" + name + "' must have at least one agent type");
        }
    }

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
