package dev.langchain4j.agentic.goap;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgentExecution;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.List;

public class GoalOrientedPlanner implements Planner {

    private String goal;

    private GoalOrientedSearchGraph graph;
    private List<AgentInstance> path;

    private int agentCursor = 0;

    @Override
    public void init(AgenticScope agenticScope, AgentInstance plannerAgent, List<AgentInstance> subagents) {
        this.goal = plannerAgent.outputKey();
        this.graph = new GoalOrientedSearchGraph(subagents);
    }

    @Override
    public Action firstAction(AgenticScope agenticScope) {
        path = graph.search(agenticScope.state().keySet(), goal);
        if (path.isEmpty()) {
            throw new IllegalStateException("No path found for goal: " + goal);
        }
        return Action.call(path.get(agentCursor++));
    }

    @Override
    public Action nextAction(AgenticScope agenticScope, AgentExecution lastAgentExecution) {
        return agentCursor >= path.size() ? Action.done() : Action.call(path.get(agentCursor++));
    }
}
