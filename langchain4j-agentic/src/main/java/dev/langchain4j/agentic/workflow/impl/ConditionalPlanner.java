package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.workflow.ConditionalAgent;
import dev.langchain4j.agentic.workflow.ConditionalAgentInstance;

import java.util.List;

public record ConditionalPlanner(List<ConditionalAgent> conditionalSubagents) implements Planner {

    @Override
    public Action firstAction(PlanningContext planningContext) {
        List<AgentInstance> agentsToCall = conditionalSubagents.stream()
                .filter(conditionalAgent -> conditionalAgent.predicate().test(planningContext.agenticScope()))
                .flatMap(conditionalAgent -> conditionalAgent.agentInstances().stream())
                .toList();
        return agentsToCall.isEmpty() ? done() : call(agentsToCall);
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        return done();
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.ROUTER;
    }

    @Override
    public boolean terminated() {
        return true;
    }

    @Override
    public <T extends AgentInstance> T as(Class<T> agentInstanceClass, AgentInstance agentInstance) {
        if (agentInstanceClass != ConditionalAgentInstance.class) {
            throw new ClassCastException("Cannot cast to " + agentInstanceClass.getName() + ": incompatible type");
        }
        return (T) new DefaultConditionalAgentInstance(agentInstance, this);
    }

}
