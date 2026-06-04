package dev.langchain4j.agentic.patterns.blackboard;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;

import java.util.List;
import java.util.function.Predicate;

/**
 * Strategy for resolving conflicts when multiple agents can fire simultaneously on the blackboard.
 * The strategy receives the current scope state and all candidate agents that are ready to fire,
 * and returns which one should be activated.
 */
@FunctionalInterface
public interface ConflictResolutionStrategy {

    /**
     * Selects the first candidate, preserving the declaration order used in the {@code subAgents} method.
     */
    ConflictResolutionStrategy DECLARATION_ORDER = (scope, candidates) -> candidates.get(0);

    AgentInstance resolve(AgenticScope scope, List<AgentInstance> candidates);

    /**
     * Returns a strategy that selects the first candidate in declaration order.
     * This is the default strategy used by {@link BlackboardPlanner} if no strategy is provided.
     */
    static ConflictResolutionStrategy declarationOrder() {
        return DECLARATION_ORDER;
    }

    /**
     * Returns a strategy that selects the candidate matching {@code agentType} only when {@code condition}
     * is satisfied, or {@code null} otherwise. Intended to be chained with {@link #or(ConflictResolutionStrategy)}.
     */
    static ConflictResolutionStrategy agentOfType(Class<?> agentType, Predicate<AgenticScope> condition) {
        return selectAgent(a -> a.type() == agentType, condition);
    }

    /**
     * Returns a strategy that unconditionally selects the candidate matching {@code agentType},
     * or {@code null} if no candidate of that type is present.
     */
    static ConflictResolutionStrategy agentOfType(Class<?> agentType) {
        return selectAgent(a -> a.type() == agentType);
    }

    /**
     * Returns a strategy that selects the candidate matching {@code agentName} only when {@code condition}
     * is satisfied, or {@code null} otherwise. Intended to be chained with {@link #or(ConflictResolutionStrategy)}.
     */
    static ConflictResolutionStrategy agentWithName(String agentName, Predicate<AgenticScope> condition) {
        return selectAgent(a -> agentName.equals(a.name()), condition);
    }

    /**
     * Returns a strategy that unconditionally selects the candidate matching {@code agentName},
     * or {@code null} if no candidate with that name is present.
     */
    static ConflictResolutionStrategy agentWithName(String agentName) {
        return selectAgent(a -> agentName.equals(a.name()));
    }

    /**
     * Returns a strategy that selects the first candidate matching {@code agentFilter} only when
     * {@code condition} is satisfied, or {@code null} otherwise.
     * Intended to be chained with {@link #or(ConflictResolutionStrategy)}.
     */
    static ConflictResolutionStrategy selectAgent(Predicate<AgentInstance> agentFilter, Predicate<AgenticScope> condition) {
        return (scope, candidates) -> {
            if (condition.test(scope)) {
                return selectAgent(agentFilter).resolve(scope, candidates);
            }
            return null;
        };
    }

    /**
     * Returns a strategy that unconditionally selects the first candidate matching {@code agentFilter},
     * or {@code null} if no candidate matches.
     */
    static ConflictResolutionStrategy selectAgent(Predicate<AgentInstance> agentFilter) {
        return (scope, candidates) -> candidates.stream()
                .filter(agentFilter)
                .findFirst()
                .orElse(null);
    }

    /**
     * Chains this strategy with a fallback: if this strategy returns {@code null},
     * the {@code other} strategy is applied instead.
     */
    default ConflictResolutionStrategy or(ConflictResolutionStrategy other) {
        return (scope, candidates) -> {
            AgentInstance result = this.resolve(scope, candidates);
            if (result != null) {
                return result;
            }
            return other.resolve(scope, candidates);
        };
    }
}
