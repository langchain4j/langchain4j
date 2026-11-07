package dev.langchain4j.agentic.planner;

import java.lang.reflect.Type;
import java.util.List;

public interface AgentInstance {

    Class<?> type();

    Class<? extends Planner> plannerType();

    String name();

    String agentId();

    String description();

    Type outputType();

    String outputKey();

    boolean async();

    default boolean optional() {
        return false;
    }

    /**
     * The condition under which this agent runs when it is invoked as a subagent, or {@code null} when it always
     * runs. When the condition is false, the agent is skipped: it is not invoked, it writes nothing to the
     * {@link dev.langchain4j.agentic.scope.AgenticScope} and its parent's planner is notified of the skip. The
     * condition is ignored when the agent is invoked directly, as the root of an agentic system.
     * <p>
     * The condition can be evaluated more than once for a single invocation, by the planner of the parent while
     * selecting its next agents and again when the agent is invoked, so it must be cheap and free of side effects.
     */
    default AgentCondition activationCondition() {
        return null;
    }

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
