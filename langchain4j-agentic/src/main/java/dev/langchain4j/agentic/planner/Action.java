package dev.langchain4j.agentic.planner;

import dev.langchain4j.agentic.internal.AgentExecutor;

public interface Action {

    boolean isDone();

    default Object result() {
        return null;
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
