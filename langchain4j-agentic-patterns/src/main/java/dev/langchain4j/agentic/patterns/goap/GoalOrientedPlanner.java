package dev.langchain4j.agentic.patterns.goap;

import java.util.List;
import java.util.Map;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.planner.Planner;

public class GoalOrientedPlanner implements Planner {

    private String goal;

    private GoalOrientedSearchGraph graph;
    private List<AgentInstance> path;

    private int agentCursor = 0;

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        this.goal = initPlanningContext.plannerAgent().outputKey();
        this.graph = new GoalOrientedSearchGraph(initPlanningContext.subagents());
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        path = graph.search(planningContext.agenticScope().state().keySet(), goal);
        if (path.isEmpty()) {
            throw new IllegalStateException("No path found for goal: " + goal);
        }
        return call(path.get(agentCursor++));
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        return agentCursor >= path.size() ? done() : call(path.get(agentCursor++));
    }

    /**
     * GoalOrientedPlanner does not persist execution state because {@link #firstAction(PlanningContext)}
     * recomputes the path from the current scope state via graph search. On recovery, completed agents'
     * outputs are already in scope, so the search produces a shorter path containing only the remaining
     * agents. The cursor resets to 0 naturally, making state persistence unnecessary and potentially
     * harmful (a stale cursor could point beyond the bounds of the recomputed path).
     */
    @Override
    public void restoreExecutionState(Map<String, Object> state) {
        // No-op: path recomputation in firstAction() handles recovery
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.SEQUENCE;
    }
}
