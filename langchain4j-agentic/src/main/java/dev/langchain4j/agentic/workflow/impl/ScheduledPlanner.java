package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgentInvocationListener;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.SchedulerService;
import java.util.List;

public class ScheduledPlanner implements Planner {

    private List<AgentInstance> agents;

    private int maxIterations;
    private String cronExpression;
    private SchedulerService schedulerService;

    public ScheduledPlanner(String cronExpression, int maxIterations, SchedulerService schedulerService) {
        this.cronExpression = cronExpression;
        this.maxIterations = maxIterations;
        this.schedulerService = schedulerService;
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        this.agents = initPlanningContext.subagents();
        this.schedulerService.start();
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        schedulerService.schedule(
                cronExpression,
                maxIterations,
                () -> execute((DefaultAgenticScope) planningContext.agenticScope(), planningContext.listener()));
        return done();
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        return done();
    }

    private void execute(DefaultAgenticScope agenticScope, AgentInvocationListener listener) {
        for (final AgentInstance agentInstance : agents) {
            ((AgentExecutor) agentInstance).execute(agenticScope, listener);
        }
    }
}
