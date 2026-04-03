package dev.langchain4j.agentic.planner;

import dev.langchain4j.agentic.internal.AgentExecutor;
import java.util.List;
import java.util.stream.Stream;

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

    class NoOpAction extends AgentCallAction {
        static final Action INSTANCE = new NoOpAction();
    }
}
