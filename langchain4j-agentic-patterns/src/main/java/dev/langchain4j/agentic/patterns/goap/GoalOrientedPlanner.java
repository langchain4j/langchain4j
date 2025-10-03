package dev.langchain4j.agentic.patterns.goap;

import java.util.List;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgentExecution;
import dev.langchain4j.agentic.scope.AgenticScope;

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
        return call(path.get(agentCursor++));
    }

    @Override
    public Action nextAction(AgenticScope agenticScope, AgentExecution previousAgentExecution) {
        return agentCursor >= path.size() ? done() : call(path.get(agentCursor++));
    }
}
