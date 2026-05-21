package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.planner.AgentInstance;

public interface LoopAgentInstance extends AgentInstance {

    int maxIterations();

    boolean testExitAtLoopEnd();

    String exitCondition();
}
