package dev.langchain4j.agentic.planner;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * A described predicate on the {@link AgenticScope} deciding whether an agent runs. It can be evaluated more than once
 * for a single invocation of the agent, so it must be cheap and free of side effects.
 *
 * @see AgentInstance#activationCondition()
 */
public final class AgentCondition {

    private final String description;
    private final Predicate<AgenticScope> predicate;

    /**
     * @param description a human-readable description of the condition, shown in logs, listeners and reports, and
     *                    sent to the model of a supervisor when it skips the agent
     * @param predicate the predicate evaluated on the {@link AgenticScope}
     */
    public AgentCondition(String description, Predicate<AgenticScope> predicate) {
        this.description = ensureNotNull(description, "description");
        this.predicate = ensureNotNull(predicate, "activationCondition");
    }

    public String description() {
        return description;
    }

    public Predicate<AgenticScope> predicate() {
        return predicate;
    }

    public boolean test(AgenticScope agenticScope) {
        return predicate.test(agenticScope);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AgentCondition that
                && description.equals(that.description)
                && predicate.equals(that.predicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, predicate);
    }

    @Override
    public String toString() {
        return "AgentCondition{description='" + description + "'}";
    }
}
