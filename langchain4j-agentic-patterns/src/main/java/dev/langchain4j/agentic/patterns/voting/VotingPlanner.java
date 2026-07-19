package dev.langchain4j.agentic.patterns.voting;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.planner.Planner;

import java.util.ArrayList;
import java.util.List;

public class VotingPlanner implements Planner {

    private final VotingStrategy strategy;

    private List<AgentInstance> subagents;
    private int completedCount;
    private final List<Object> votes = new ArrayList<>();

    public VotingPlanner() {
        this(VotingStrategy.majority());
    }

    public VotingPlanner(VotingStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        this.subagents = initPlanningContext.subagents();
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        if (subagents.isEmpty()) {
            return done();
        }
        return call(subagents);
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        votes.add(planningContext.previousAgentInvocation().output());
        completedCount++;

        if (completedCount < subagents.size()) {
            return noOp();
        }

        return done(strategy.aggregate(votes));
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.PARALLEL;
    }
}
