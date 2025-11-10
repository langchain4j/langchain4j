package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.PlannerRequest;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;

import java.util.List;
import java.util.function.Predicate;

public record ConditionalPlanner(List<ConditionalAgent> conditionalAgents) implements Planner {

    record ConditionalAgent(Predicate<AgenticScope> condition, List<AgentInstance> agentInstances) { }

    @Override
    public Action firstAction(PlannerRequest plannerRequest) {
        List<AgentInstance> agentsToCall = conditionalAgents.stream()
                .filter(conditionalAgent -> conditionalAgent.condition.test(plannerRequest.agenticScope()))
                .flatMap(conditionalAgent -> conditionalAgent.agentInstances.stream())
                .toList();
        return call(agentsToCall);
    }

    @Override
    public Action nextAction(PlannerRequest plannerRequest) {
        return done();
    }
}
