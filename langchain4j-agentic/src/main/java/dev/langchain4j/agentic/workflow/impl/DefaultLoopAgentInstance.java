package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.workflow.LoopAgentInstance;

public class DefaultLoopAgentInstance extends AbstractAgentInstance implements LoopAgentInstance {

    private final LoopPlanner planner;

    public DefaultLoopAgentInstance(AgentInstance delegate, LoopPlanner planner) {
        super(delegate);
        this.planner = planner;
    }

    @Override
    public int maxIterations() {
        return planner.maxIterations();
    }

    @Override
    public boolean testExitAtLoopEnd() {
        return planner.testExitAtLoopEnd();
    }

    @Override
    public String exitCondition() {
        return planner.exitCondition();
    }
}
