package dev.langchain4j.agentic.workflow.impl;

import java.util.List;
import java.util.function.BiPredicate;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.workflow.LoopAgentInstance;

public class LoopPlanner implements Planner {

    private final int maxIterations;
    private int iterationsCounter = 1;

    private final boolean testExitAtLoopEnd;

    private final BiPredicate<AgenticScope, Integer> exitCondition;
    private final String exitConditionDescription;

    private List<AgentInstance> agents;
    private int agentCursor = 0;

    public LoopPlanner(int maxIterations, boolean testExitAtLoopEnd, BiPredicate<AgenticScope, Integer> exitCondition, String exitConditionDescription) {
        this.maxIterations = maxIterations;
        this.testExitAtLoopEnd = testExitAtLoopEnd;
        this.exitCondition = exitCondition;
        this.exitConditionDescription = exitConditionDescription;
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        this.agents = initPlanningContext.subagents();
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        return call(agents.get(agentCursor));
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        agentCursor = (agentCursor+1) % agents.size();
        if (agentCursor == 0) {
            if (iterationsCounter > maxIterations || exitCondition.test(planningContext.agenticScope(), iterationsCounter)) {
                return done();
            }
            iterationsCounter++;
        } else if (!testExitAtLoopEnd && exitCondition.test(planningContext.agenticScope(), iterationsCounter)) {
            return done();
        }
        return call(agents.get(agentCursor));
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.LOOP;
    }

    @Override
    public <T extends AgentInstance> T as(Class<T> agentInstanceClass, AgentInstance agentInstance) {
        if (agentInstanceClass != LoopAgentInstance.class) {
            throw new ClassCastException("Cannot cast to " + agentInstanceClass.getName() + ": incompatible type");
        }
        return (T) new DefaultLoopAgentInstance(agentInstance, this);
    }

    public int maxIterations() {
        return maxIterations;
    }

    public boolean testExitAtLoopEnd() {
        return testExitAtLoopEnd;
    }

    public String exitCondition() {
        return exitConditionDescription;
    }
}
