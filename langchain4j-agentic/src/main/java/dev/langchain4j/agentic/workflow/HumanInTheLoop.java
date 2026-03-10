package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.internal.AgentSpecsProvider;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.function.Function;
import java.util.function.Supplier;

public record HumanInTheLoop(
        String outputKey,
        String description,
        boolean async,
        Function<AgenticScope, ?> responseProvider,
        AgentListener listener)
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

        public HumanInTheLoopBuilder listener(AgentListener agentListener) {
            this.agentListener = agentListener;
            return this;
        }

        public HumanInTheLoop build() {
            return new HumanInTheLoop(outputKey, description, async, responseProvider, agentListener);
        }
    }
}
