package dev.langchain4j.agentic.workflow.impl;

import java.util.List;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.PlannerRequest;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;

public class ParallelPlanner implements Planner {

    private List<AgentInstance> agents;

    @Override
    public void init(AgenticScope agenticScope, AgentInstance plannerAgent, List<AgentInstance> subagents) {
        this.agents = subagents;
    }

    @Override
    public Action firstAction(PlannerRequest plannerRequest) {
        return call(agents);
    }

    @Override
    public Action nextAction(PlannerRequest plannerRequest) {
        return done();
    }
}
