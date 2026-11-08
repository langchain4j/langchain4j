package dev.langchain4j.agentic.patterns.goap;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import java.util.List;
import java.util.Map;

/**
 * A planner that computes the shortest sequence of agents leading from the current scope state to a goal.
 * <p>
 * The goal is the output key of the planner agent. A goal-oriented system without a goal has nothing to
 * plan towards, so configuring the planner agent without an output key is rejected with an
 * {@link AgenticSystemConfigurationException}.
 */
public class GoalOrientedPlanner implements Planner {

    private String goal;

    private List<AgentInstance> subagents;
    private List<AgentInstance> path;

    private int agentCursor = 0;

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        this.goal = initPlanningContext.plannerAgent().outputKey();
        if (isNullOrBlank(this.goal)) {
            throw new AgenticSystemConfigurationException("A GoalOrientedPlanner needs a goal to plan towards, but"
                    + " the agent '" + initPlanningContext.plannerAgent().name()
                    + "' declares no output key. Set an output key on the planner agent to the state that the plan"
                    + " should produce.");
        }
        this.subagents = initPlanningContext.subagents();
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        // an agent whose activation condition is false can't be part of the path
        List<AgentInstance> activeAgents =
                subagents.stream().filter(planningContext::isActive).toList();
        path = new GoalOrientedSearchGraph(activeAgents)
                .search(planningContext.agenticScope().state().keySet(), goal);
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
