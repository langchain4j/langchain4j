package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.internal.AgentSpecsProvider;
import java.util.function.Consumer;
import java.util.function.Supplier;

public record HumanInTheLoop(
        String inputKey,
        String outputKey,
        String description,
        Consumer<?> requestWriter,
        boolean async,
        Supplier<?> responseReader)
        implements AgentSpecsProvider {

    @Agent("An agent that asks the user for missing information")
    public Object askUser(Object request) {
        ((Consumer<Object>) requestWriter).accept(request);
        return responseReader.get();
    }

    public static class HumanInTheLoopBuilder {

        private String inputKey = "request";
        private String outputKey = "response";
        private String description = "An agent that asks the user for missing information";
        private boolean async = false;
        private Consumer<?> requestWriter;
        private Supplier<?> responseReader;

        public HumanInTheLoopBuilder requestWriter(Consumer<?> requestWriter) {
            this.requestWriter = requestWriter;
            return this;
        }

        public HumanInTheLoopBuilder responseReader(Supplier<?> responseReader) {
            this.responseReader = responseReader;
            return this;
        }

        public HumanInTheLoopBuilder inputKey(String inputKey) {
            this.inputKey = inputKey;
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

        public HumanInTheLoop build() {
            return new HumanInTheLoop(inputKey, outputKey, description, requestWriter, async, responseReader);
        }
    }
}
