package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgentInstance;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static dev.langchain4j.agentic.workflow.AgentExecutor.agentsToExecutors;

public class SequentialAgent implements WorkflowAgent {

    private final List<AgentExecutor> agentExecutors;

    private SequentialAgent(Builder builder) {
        this.agentExecutors = agentsToExecutors(builder.agents);
    }

    @Override
    @Agent(name = "SEQUENTIAL" + WORKFLOW_AGENT_SUFFIX)
    public Map<String, Object> invoke(Map<String, Object> input) {
        Map<String, Object> state = new HashMap<>(input);
        for (AgentExecutor agentExecutor : agentExecutors) {
            Object result = agentExecutor.invoke(state);
            if (agentExecutor.isWorkflowAgent()) {
                state = (Map<String, Object>) result;
            }
        }
        return state;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<AgentInstance> agents;

        public SequentialAgent build() {
            return new SequentialAgent(this);
        }

        public Builder subAgents(Object... agents) {
            this.agents = Stream.of(agents).map(AgentInstance.class::cast).toList();
            return this;
        }
    }
}
