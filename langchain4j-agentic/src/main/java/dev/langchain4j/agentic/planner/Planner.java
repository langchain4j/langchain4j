package dev.langchain4j.agentic.planner;

import java.util.List;
import java.util.Map;

public interface Planner {

    default void init(InitPlanningContext initPlanningContext) { }

    /**
     * Returns the planner's current execution state as a map of serializable values.
     * This state is persisted to the {@link dev.langchain4j.agentic.scope.AgenticScope} after each
     * agent invocation, enabling the planner to resume from the correct position after a crash.
     * <p>
     * The returned state must be such that, when passed to {@link #restoreExecutionState(Map)} and
     * {@link #firstAction(PlanningContext)} is called, the planner produces the correct resume action.
     * <p>
     * Stateless planners (e.g., parallel, conditional) can use the default empty implementation.
     *
     * @return a map of state entries to persist, or an empty map if no state needs saving
     */
    default Map<String, Object> executionState() {
        return Map.of();
    }

    /**
     * Restores the planner's execution state from a previously saved map.
     * Called by the execution loop before {@link #firstAction(PlanningContext)} when recovering
     * from a persisted scope.
     *
     * @param state the previously saved execution state
     */
    default void restoreExecutionState(Map<String, Object> state) { }

    default Action firstAction(PlanningContext planningContext) {
        return nextAction(planningContext);
    }

    default AgenticSystemTopology topology() {
        return AgenticSystemTopology.SEQUENCE;
    }

    Action nextAction(PlanningContext planningContext);

    default boolean terminated() {
        return false;
    }

    default Action noOp() {
        return Action.NoOpAction.INSTANCE;
    }

    default Action call(AgentInstance... agents) {
        return new Action.AgentCallAction(agents);
    }

    default Action call(List<AgentInstance> agents) {
        return call(agents.toArray(new AgentInstance[0]));
    }

    default Action done() {
        return Action.DoneAction.INSTANCE;
    }

    default Action done(Object result) {
        return new Action.DoneWithResultAction(result);
    }

    default <T extends AgentInstance> T as(Class<T> agentInstanceClass, AgentInstance agentInstance) {
        throw new ClassCastException("Cannot cast to " + agentInstanceClass.getName() + ": incompatible type");
    }
}
