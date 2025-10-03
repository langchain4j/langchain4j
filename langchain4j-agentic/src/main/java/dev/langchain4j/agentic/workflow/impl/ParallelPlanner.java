package dev.langchain4j.agentic.workflow.impl;

import java.util.List;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgentExecution;
import dev.langchain4j.agentic.scope.AgenticScope;

public class ParallelPlanner implements Planner {

    private List<AgentInstance> agents;

    @Override
    public void init(AgenticScope agenticScope, List<AgentInstance> agents) {
        this.agents = agents;
    }

    @Override
    public Action firstAction(AgenticScope agenticScope) {
        return Action.call(agents);
    }
}
