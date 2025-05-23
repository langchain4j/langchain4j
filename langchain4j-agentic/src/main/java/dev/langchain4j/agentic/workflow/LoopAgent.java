package dev.langchain4j.agentic.workflow;

import static dev.langchain4j.agentic.workflow.AgentExecutor.agentsToExecutors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgentInstance;

public class LoopAgent implements WorkflowAgent {

    private final List<AgentExecutor> agentExecutors;
    private final int maxIterations;
    private final Predicate<Map<String, Object>> exitCondition;

    private LoopAgent(Builder builder) {
        this.agentExecutors = agentsToExecutors(builder.agents);
        this.maxIterations = builder.maxIterations;
        this.exitCondition = builder.exitCondition;
    }

    @Override
    @Agent(name = "LOOP" + WORKFLOW_AGENT_SUFFIX)
    public Map<String, Object> invoke(Map<String, Object> input) {
        Map<String, Object> state = new HashMap<>(input);
        for (int i = 0; i < maxIterations; i++) {
            for (AgentExecutor agentExecutor : agentExecutors) {
                Object result = agentExecutor.invoke(state);
                if (agentExecutor.isWorkflowAgent()) {
                    state = (Map<String, Object>) result;
                }
                if (exitCondition.test(state)) {
                    return state;
                }
            }
        }
        return state;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<AgentInstance> agents;
        private int maxIterations = Integer.MAX_VALUE;
        private Predicate<Map<String, Object>> exitCondition = state -> false;

        public LoopAgent build() {
            return new LoopAgent(this);
        }

        public Builder subAgents(Object... agents) {
            this.agents = Stream.of(agents).map(AgentInstance.class::cast).toList();
            return this;
        }

        public Builder maxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        public Builder exitCondition(Predicate<Map<String, Object>> exitCondition) {
            this.exitCondition = exitCondition;
            return this;
        }
    }
}
