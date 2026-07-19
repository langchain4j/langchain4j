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

    /**
     * Returns the first action to execute when the planner starts (or resumes).
     * Defaults to delegating to {@link #nextAction(PlanningContext)}.
     *
     * @param planningContext the current planning context
     * @return the first action to execute
     */
    default Action firstAction(PlanningContext planningContext) {
        return nextAction(planningContext);
    }

    /**
     * Returns the topology of the agentic system managed by this planner.
     *
     * @return the topology (defaults to {@link AgenticSystemTopology#SEQUENCE})
     */
    default AgenticSystemTopology topology() {
        return AgenticSystemTopology.SEQUENCE;
    }

    /**
     * Determines the next action to execute based on the result of the previous agent invocation.
     *
     * @param planningContext the current planning context, including the previous agent's result
     * @return the next action to execute
     */
    Action nextAction(PlanningContext planningContext);

    /**
     * Returns {@code true} if the planner has reached a terminal state and will not
     * produce further actions.
     *
     * @return {@code true} if the planner is terminated
     */
    default boolean terminated() {
        return false;
    }

    /**
     * Returns a no-op action that yields control without invoking any agent.
     *
     * @return a no-op action
     */
    default Action noOp() {
        return Action.NoOpAction.INSTANCE;
    }

    /**
     * Returns an action that invokes the given agents. Multiple agents are dispatched in parallel.
     *
     * @param agents the agents to invoke
     * @return an agent call action
     */
    default Action call(AgentInstance... agents) {
        return new Action.AgentCallAction(agents);
    }

    /**
     * Returns an action that invokes the given agents. Multiple agents are dispatched in parallel.
     *
     * @param agents the agents to invoke
     * @return an agent call action
     */
    default Action call(List<AgentInstance> agents) {
        return call(agents.toArray(new AgentInstance[0]));
    }

    /**
     * Returns an action signaling that the planner has completed with no explicit result.
     *
     * @return a done action
     */
    default Action done() {
        return Action.DoneAction.INSTANCE;
    }

    /**
     * Returns an action signaling that the planner has completed with the given result.
     *
     * @param result the result value
     * @return a done action carrying the result
     */
    default Action done(Object result) {
        return new Action.DoneWithResultAction(result);
    }

    /**
     * Returns an action signaling that the agentic system should suspend.
     * The execution loop will checkpoint the scope state and release the calling thread.
     *
     * @return a suspend action
     */
    default Action suspend() {
        return Action.SuspendAction.INSTANCE;
    }

    default <T extends AgentInstance> T as(Class<T> agentInstanceClass, AgentInstance agentInstance) {
        throw new ClassCastException("Cannot cast to " + agentInstanceClass.getName() + ": incompatible type");
    }
}
