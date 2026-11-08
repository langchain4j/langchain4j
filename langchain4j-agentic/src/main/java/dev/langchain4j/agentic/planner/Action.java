package dev.langchain4j.agentic.planner;

import dev.langchain4j.agentic.internal.AgentExecutor;
import java.util.List;
import java.util.stream.Stream;

/**
 * Represents the outcome of a {@link Planner} decision. Each action tells the execution loop
 * what to do next: invoke one or more agents, signal completion, or suspend the agentic system.
 */
public interface Action {

    /**
     * Returns {@code true} if this action signals that the planner has finished
     * (either completed or suspended) and the execution loop should exit.
     *
     * @return {@code true} if the planner is done or suspended
     */
    boolean isDone();

    /**
     * Returns {@code true} if this action represents a suspension of the agentic system.
     * A suspended action is also done ({@link #isDone()} returns {@code true}).
     *
     * @return {@code true} if the agentic system should suspend
     */
    default boolean isSuspended() {
        return false;
    }

    /**
     * Returns the result produced by the planner, if any.
     *
     * @return the result, or {@code null} if no result is available
     */
    default Object result() {
        return null;
    }

    /**
     * Signals that the planner has completed successfully with no explicit result.
     * The execution loop reads the final output from the scope state.
     */
    class DoneAction implements Action {

        static final Action INSTANCE = new DoneAction();

        @Override
        public String toString() {
            return "DoneAction";
        }

        @Override
        public boolean isDone() {
            return true;
        }
    }

    /**
     * Signals that the planner has completed successfully with an explicit result value.
     */
    class DoneWithResultAction implements Action {

        private final Object result;

        public DoneWithResultAction(final Object result) {
            this.result = result;
        }

        @Override
        public String toString() {
            return "DoneAction[" + result + "]";
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Object result() {
            return result;
        }
    }

    /**
     * Requests that the execution loop invoke one or more agents. When multiple agents
     * are listed they are dispatched in parallel.
     */
    class AgentCallAction implements Action {

        private final List<AgentExecutor> agents;

        public AgentCallAction(AgentInstance... agents) {
            this(Stream.of(agents).map(AgentExecutor.class::cast).toList());
        }

        public AgentCallAction(List<AgentExecutor> agents) {
            this.agents = agents;
        }

        @Override
        public String toString() {
            return "AgentCallAction";
        }

        @Override
        public boolean isDone() {
            return false;
        }

        public List<AgentExecutor> agentsToCall() {
            return agents;
        }
    }

    /**
     * A no-op action that yields control back to the execution loop without invoking any agent.
     * Used by planners that need to wait for an external event before deciding the next step.
     */
    class NoOpAction extends AgentCallAction {
        static final Action INSTANCE = new NoOpAction();
    }

    /**
     * Signals that the agentic system should suspend. The execution loop checkpoints the
     * scope state and throws {@link dev.langchain4j.agentic.scope.AgenticSystemSuspendedException}
     * (or returns a suspended {@link dev.langchain4j.agentic.scope.ResultWithAgenticScope})
     * to release the calling thread.
     */
    class SuspendAction implements Action {

        static final Action INSTANCE = new SuspendAction();

        @Override
        public String toString() {
            return "SuspendAction";
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public boolean isSuspended() {
            return true;
        }
    }
}
