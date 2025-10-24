package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgentExecution;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.List;

public class SequentialPlanner implements Planner {

    private List<AgentInstance> agents;
    private int agentCursor = 0;

    @Override
    public void init(AgenticScope agenticScope, AgentInstance plannerAgent, List<AgentInstance> subagents) {
        this.agents = subagents;
    }

    @Override
    public Action firstAction(AgenticScope agenticScope) {
        return Action.call(agents.get(agentCursor++));
    }

    @Override
    public Action nextAction(AgenticScope agenticScope, AgentExecution lastAgentExecution) {
        return agentCursor >= agents.size() ? Action.done() : Action.call(agents.get(agentCursor++));
    }
}
