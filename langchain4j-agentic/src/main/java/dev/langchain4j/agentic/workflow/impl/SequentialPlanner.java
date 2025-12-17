package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import java.util.List;

public class SequentialPlanner implements Planner {

    private List<AgentInstance> agents;
    private int agentCursor = 0;

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        this.agents = initPlanningContext.subagents();
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        return agentCursor >= agents.size() ? done() : call(agents.get(agentCursor++));
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.SEQUENCE;
    }
}
