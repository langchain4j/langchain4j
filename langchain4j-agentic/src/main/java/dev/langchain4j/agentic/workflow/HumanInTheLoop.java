package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.internal.AgentSpecsProvider;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.scope.AgenticScope;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public record HumanInTheLoop(
        String outputKey,
        String description,
        boolean async,
        Function<AgenticScope, ?> responseProvider,
        AgentListener listener,
        List<AgentArgument> arguments)
        implements AgentSpecsProvider {

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
            this.agentListener = agentListener;
            return this;
        }

        public HumanInTheLoop build() {
            return new HumanInTheLoop(outputKey, description, async, responseProvider, agentListener, arguments);
        }
    }
}
