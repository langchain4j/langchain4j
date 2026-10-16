package dev.langchain4j.agentic.planner;

import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.function.Predicate;

/**
 * A described predicate on the {@link AgenticScope} deciding whether an agent runs. It can be evaluated more than once
 * for a single invocation of the agent, so it must be cheap and free of side effects.
 *
 * @param description a human-readable description of the condition, shown in logs, listeners and reports
 * @param predicate the predicate evaluated on the {@link AgenticScope}
 * @see AgentInstance#activationCondition()
 */
public record AgentCondition(String description, Predicate<AgenticScope> predicate) {

    public boolean test(AgenticScope agenticScope) {
        return predicate.test(agenticScope);
    }
}
