package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import java.util.List;
import java.util.Map;

public class SequentialPlanner implements Planner {

    private List<AgentInstance> agents;
    private int agentCursor = 0;

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        this.agents = initPlanningContext.subagents();
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        return terminated() ? done() : call(agents.get(agentCursor++));
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.SEQUENCE;
    }

    @Override
    public boolean terminated() {
        return agentCursor >= agents.size();
    }

    @Override
    public Map<String, Object> executionState() {
        // Save cursor - 1: the agent that was just scheduled for execution.
        // On recovery, firstAction() delegates to nextAction() which calls agents.get(agentCursor++),
        // so the restored cursor must point to the agent that needs to be (re-)executed.
        return agentCursor > 0 ? Map.of("cursor", agentCursor - 1) : Map.of();
    }

    @Override
    public void restoreExecutionState(Map<String, Object> state) {
        Object savedCursor = state.get("cursor");
        if (savedCursor instanceof Number n) {
            this.agentCursor = n.intValue();
        }
    }
}
