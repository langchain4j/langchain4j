package dev.langchain4j.agentic.workflow.impl;

import static dev.langchain4j.agentic.internal.AgentUtil.addWorkflowStreamingAgent;
import static dev.langchain4j.agentic.internal.AgentUtil.allHaveSameOutput;
import static dev.langchain4j.agentic.internal.AgentUtil.getLastAgent;
import static dev.langchain4j.agentic.internal.AgentUtil.hasSameOutput;
import static dev.langchain4j.agentic.internal.AgentUtil.hasStreamingAgent;
import static dev.langchain4j.agentic.internal.AgentUtil.isAllStreamingAgent;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public record ConditionalPlanner(List<ConditionalAgent> conditionalAgents) implements Planner {

    record ConditionalAgent(Predicate<AgenticScope> condition, List<AgentInstance> agentInstances) {}

    @Override
    public Action firstAction(PlanningContext planningContext) {
        List<AgentInstance> agentsToCall = conditionalAgents.stream()
                .filter(conditionalAgent -> conditionalAgent.condition.test(planningContext.agenticScope()))
                .flatMap(conditionalAgent -> conditionalAgent.agentInstances.stream())
                .toList();
        return call(agentsToCall);
    }

    @Override
    public void checkSubAgents(List<AgentInstance> subAgents, AgentInstance plannerAgent) {
        List<AgentInstance> list = new ArrayList<>();
        for (ConditionalAgent conditionalAgent : this.conditionalAgents) {
            list.addAll(conditionalAgent.agentInstances());
        }

        if (hasStreamingAgent(list)) {
            if (isAllStreamingAgent(list)) {
                // All agents in a conditional workflow have the same outputKey.
                if (allHaveSameOutput(list)) {
                    AgentInstance lastAgent = getLastAgent(list);
                    // The outputKey is also the output of the conditional workflow itself.
                    if (hasSameOutput(lastAgent, plannerAgent)) {
                        // Consider the workflow is a streaming. for processing they are themselves subagent or a more
                        // complex workflow.
                        addWorkflowStreamingAgent(plannerAgent.agentId());
                    } else {
                        throw new IllegalArgumentException(
                                "The agents and the workflow should have the same outputKey.");
                    }
                } else {
                    throw new IllegalArgumentException(
                            "It needs all agents in the conditional workflow have the same outputKey.");
                }
            } else {
                throw new IllegalArgumentException(
                        "Part of the sub-agents return TokenStream, it needs all agents have the same return type.");
            }
        }
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        return done();
    }
}
