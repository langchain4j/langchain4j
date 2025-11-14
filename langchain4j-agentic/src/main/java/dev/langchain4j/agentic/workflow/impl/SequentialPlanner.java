package dev.langchain4j.agentic.workflow.impl;

import static dev.langchain4j.agentic.internal.AgentUtil.addWorkflowStreamingAgent;
import static dev.langchain4j.agentic.internal.AgentUtil.getLastAgent;
import static dev.langchain4j.agentic.internal.AgentUtil.hasSameOutput;
import static dev.langchain4j.agentic.internal.AgentUtil.hasStreamingAgent;
import static dev.langchain4j.agentic.internal.AgentUtil.isOnlyLastStreamingAgent;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import java.util.List;

public class SequentialPlanner implements Planner {

    private List<AgentInstance> agents;
    private int agentCursor = 0;

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        this.agents = initPlanningContext.subagents();
    }

    @Override
    public void checkSubAgents(List<AgentInstance> subAgents, AgentInstance plannerAgent) {
        if (hasStreamingAgent(subAgents)) {
            if (isOnlyLastStreamingAgent(subAgents)) {
                AgentInstance lastAgent = getLastAgent(subAgents);
                // The outputKey of the last agent in a sequential workflow is the same outputKey of the workflow
                // itself.
                if (hasSameOutput(lastAgent, plannerAgent)) {
                    // Consider the workflow is a streaming. for processing they are themselves subagent or a more
                    // complex workflow.
                    addWorkflowStreamingAgent(plannerAgent.agentId());
                } else {
                    throw new IllegalArgumentException(
                            "The last sub-agent and the workflow should have the same outputKey.");
                }
            } else {
                throw new IllegalArgumentException("Only the last sub-agent can return TokenStream.");
            }
        }
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        return agentCursor >= agents.size() ? done() : call(agents.get(agentCursor++));
    }
}
