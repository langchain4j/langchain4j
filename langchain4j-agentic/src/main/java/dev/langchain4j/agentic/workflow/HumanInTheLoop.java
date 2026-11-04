package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.internal.AgentSpecsProvider;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.ComposedAgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentCondition;
import dev.langchain4j.agentic.scope.AgenticScope;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public record HumanInTheLoop(
        String outputKey,
        String description,
        boolean async,
        Function<AgenticScope, ?> responseProvider,
        AgentListener listener,
        List<AgentArgument> arguments,
        AgentCondition activationCondition)
        implements AgentSpecsProvider {

    public HumanInTheLoop(
            String outputKey,
            String description,
            boolean async,
            Function<AgenticScope, ?> responseProvider,
            AgentListener listener,
            List<AgentArgument> arguments) {
        this(outputKey, description, async, responseProvider, listener, arguments, null);
    }

    @Agent("An agent that asks the user for missing information")
    public Object askUser(AgenticScope scope) {
        return responseProvider.apply(scope);
    }

    public static class HumanInTheLoopBuilder {

        private String outputKey = "response";
        private String description = "An agent that asks the user for missing information";
        private boolean async = false;
        private Function<AgenticScope, ?> responseProvider;
        private AgentListener agentListener;
        private List<AgentArgument> arguments;
        private AgentCondition activationCondition;

        public HumanInTheLoopBuilder responseProvider(Supplier<?> responseProvider) {
            return responseProvider(scope -> responseProvider.get());
        }

        public HumanInTheLoopBuilder responseProvider(Function<AgenticScope, ?> responseProvider) {
            this.responseProvider = responseProvider;
            return this;
        }

        public HumanInTheLoopBuilder outputKey(String outputKey) {
            this.outputKey = outputKey;
            return this;
        }

        public HumanInTheLoopBuilder description(String description) {
            this.description = description;
            return this;
        }

        public HumanInTheLoopBuilder async(boolean async) {
            this.async = async;
            return this;
        }

        public HumanInTheLoopBuilder inputs(List<AgentArgument> inputs) {
            this.arguments = inputs;
            return this;
        }

        public HumanInTheLoopBuilder inputKey(Class<?> type, String name) {
            this.arguments = List.of(new AgentArgument(type, name));
            return this;
        }

        public HumanInTheLoopBuilder inputKeys(Class<?> type1, String name1, Class<?> type2, String name2) {
            this.arguments = List.of(new AgentArgument(type1, name1), new AgentArgument(type2, name2));
            return this;
        }

        public HumanInTheLoopBuilder inputKeys(Class<?> type1, String name1, Class<?> type2, String name2,
                                                Class<?> type3, String name3) {
            this.arguments = List.of(new AgentArgument(type1, name1), new AgentArgument(type2, name2),
                    new AgentArgument(type3, name3));
            return this;
        }

        public HumanInTheLoopBuilder inputKeys(Class<?> type1, String name1, Class<?> type2, String name2,
                                                Class<?> type3, String name3, Class<?> type4, String name4) {
            this.arguments = List.of(new AgentArgument(type1, name1), new AgentArgument(type2, name2),
                    new AgentArgument(type3, name3), new AgentArgument(type4, name4));
            return this;
        }

        public HumanInTheLoopBuilder listener(AgentListener agentListener) {
            this.agentListener = ComposedAgentListener.compose(this.agentListener, agentListener);
            return this;
        }

        /**
         * Sets the condition under which this agent runs when it is invoked as a subagent of an agentic system.
         * When the condition is false the user is not asked anything and the agent is skipped. It can be evaluated
         * more than once, so it must be free of side effects.
         */
        public HumanInTheLoopBuilder activationCondition(Predicate<AgenticScope> activationCondition) {
            return activationCondition("<unknown>", activationCondition);
        }

        /**
         * Sets the condition, with its description, under which this agent runs when it is invoked as a subagent of
         * an agentic system. When the condition is false the user is not asked anything and the agent is skipped.
         * It can be evaluated more than once, so it must be free of side effects.
         */
        public HumanInTheLoopBuilder activationCondition(
                String description, Predicate<AgenticScope> activationCondition) {
            this.activationCondition = new AgentCondition(description, activationCondition);
            return this;
        }

        public HumanInTheLoop build() {
            return new HumanInTheLoop(
                    outputKey, description, async, responseProvider, agentListener, arguments, activationCondition);
        }
    }
}
