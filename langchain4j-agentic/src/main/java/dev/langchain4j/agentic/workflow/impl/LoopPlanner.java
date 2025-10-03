package dev.langchain4j.agentic.workflow.impl;

import java.util.List;
import java.util.function.BiPredicate;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgentExecution;
import dev.langchain4j.agentic.scope.AgenticScope;

public class LoopPlanner implements Planner {

    private final int maxIterations;
    private int iterationsCounter = 1;

    private final boolean testExitAtLoopEnd;

    private final BiPredicate<AgenticScope, Integer> exitCondition;

    private List<AgentInstance> agents;
    private int agentCursor = 0;

    public LoopPlanner(int maxIterations, boolean testExitAtLoopEnd, BiPredicate<AgenticScope, Integer> exitCondition) {
        this.maxIterations = maxIterations;
        this.testExitAtLoopEnd = testExitAtLoopEnd;
        this.exitCondition = exitCondition;
    }

    @Override
    public void init(AgenticScope agenticScope, AgentInstance plannerAgent, List<AgentInstance> subagents) {
        this.agents = subagents;
    }

    @Override
    public Action firstAction(AgenticScope agenticScope) {
        return call(agents.get(agentCursor));
    }

    @Override
    public Action nextAction(AgenticScope agenticScope, AgentExecution previousAgentExecution) {
        agentCursor = (agentCursor+1) % agents.size();
        if (agentCursor == 0) {
            if (iterationsCounter > maxIterations || exitCondition.test(agenticScope, iterationsCounter)) {
                return done();
            }
            iterationsCounter++;
        } else if (!testExitAtLoopEnd && exitCondition.test(agenticScope, iterationsCounter)) {
            return done();
        }
        return call(agents.get(agentCursor));
    }
}
