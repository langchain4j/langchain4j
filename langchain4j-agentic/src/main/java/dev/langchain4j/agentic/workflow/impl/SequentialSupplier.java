package dev.langchain4j.agentic.workflow.impl;

import static dev.langchain4j.agentic.internal.AgentUtil.getLastAgent;
import static dev.langchain4j.agentic.internal.AgentUtil.hasStreamingAgent;
import static dev.langchain4j.agentic.internal.AgentUtil.isOnlyLastStreamingAgent;

import dev.langchain4j.agentic.internal.StreamingSubAgentsChecker;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.Planner;
import java.util.List;
import java.util.function.Supplier;

public class SequentialSupplier implements Supplier<Planner>, StreamingSubAgentsChecker {

    @Override
    public boolean checkSubAgents(List<AgentInstance> subAgents, String plannerAgentOutputKey) {
        if (hasStreamingAgent(subAgents)) {
            if (isOnlyLastStreamingAgent(subAgents)) {
                AgentInstance lastAgent = getLastAgent(subAgents);
                // The outputKey of the last agent in a sequential workflow is the same outputKey of the
                // workflow itself.
                if (lastAgent.outputKey().equals(plannerAgentOutputKey)) {
                    // Consider the workflow is a streaming. for processing they are themselves subagent or a more
                    // complex workflow.
                    return true;
                } else {
                    throw new IllegalArgumentException(
                            "The last sub-agent and the workflow should have the same outputKey.");
                }
            } else {
                throw new IllegalArgumentException("Only the last sub-agent can return TokenStream.");
            }
        }
        return false;
    }

    @Override
    public Planner get() {
        return new SequentialPlanner();
    }
}
