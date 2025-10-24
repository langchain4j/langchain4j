package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgentExecution;
import dev.langchain4j.agentic.scope.AgenticScope;

import java.util.List;
import java.util.function.Predicate;

public record ConditionalPlanner(List<ConditionalAgent> conditionalAgents) implements Planner {

    record ConditionalAgent(Predicate<AgenticScope> condition, List<AgentInstance> agentInstances) { }

    @Override
    public Action firstAction(AgenticScope agenticScope) {
        List<AgentInstance> agentsToCall = conditionalAgents.stream()
                .filter(conditionalAgent -> conditionalAgent.condition.test(agenticScope))
                .flatMap(conditionalAgent -> conditionalAgent.agentInstances.stream())
                .toList();
        return Action.call(agentsToCall);
    }
}
