package dev.langchain4j.agentic.planner;

import dev.langchain4j.agentic.internal.AgentExecutor;
import java.util.List;

public interface Action {

    boolean isDone();

    static Action noOp() {
        return Action.AgentCallAction.NO_OP;
    }

    static Action call(AgentInstance... agents) {
        return new Action.AgentCallAction(agents);
    }

    static Action call(List<AgentInstance> agents) {
        return call(agents.toArray(new AgentInstance[0]));
    }

    static Action done() {
        return Action.DoneAction.INSTANCE;
    }

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

    class AgentCallAction implements Action {

        static final Action NO_OP = new AgentCallAction();

        private final AgentExecutor[] agents;

        AgentCallAction(AgentInstance... agents) {
            this.agents = new AgentExecutor[agents.length];
            for (int i = 0; i < agents.length; i++) {
                this.agents[i] = (AgentExecutor) agents[i];
            }
        }

        @Override
        public String toString() {
            return "AgentCallAction";
        }

        @Override
        public boolean isDone() {
            return false;
        }

        public AgentExecutor[] agentsToCall() {
            return agents;
        }
    }
}
