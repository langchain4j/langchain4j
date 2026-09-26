package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import java.util.List;
import java.util.Map;

public class SequentialPlanner implements Planner {

    private List<AgentInstance> agents;
    private int agentCursor = 0;

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        this.agents = initPlanningContext.subagents();

        java.util.Set<String> availableKeys = new java.util.HashSet<>(
                initPlanningContext.agenticScope().state().keySet());

        boolean scopeMightBeMutated = initPlanningContext.agenticScope()
                        instanceof dev.langchain4j.agentic.scope.DefaultAgenticScope defaultScope
                && defaultScope.hasCustomErrorHandler();

        for (AgentInstance agent : this.agents) {
            if (!agent.optional() && !scopeMightBeMutated) {
                for (dev.langchain4j.agentic.planner.AgentArgument arg : agent.arguments()) {
                    String name = arg.name();
                    if (!arg.isOptional() && arg.defaultValue() == null && !name.startsWith("@")) {
                        if (!availableKeys.contains(name)) {
                            throw new dev.langchain4j.agentic.agent.MissingArgumentException(name);
                        }
                    }
                }
            }
            if (agent.outputKey() != null) {
                availableKeys.add(agent.outputKey());
            }

            if (mightMutateScope(agent)) {
                scopeMightBeMutated = true;
            }
        }
    }

    private boolean mightMutateScope(AgentInstance agent) {
        if (agent.arguments().stream().anyMatch(arg -> "@AgenticScope".equals(arg.name()))) {
            return true;
        }
        if (agent.outputType() != null
                && agent.outputType()
                        .getTypeName()
                        .startsWith("dev.langchain4j.agentic.scope.ResultWithAgenticScope")) {
            return true;
        }
        for (AgentInstance subagent : agent.subagents()) {
            if (mightMutateScope(subagent)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        return terminated() ? done() : call(agents.get(agentCursor++));
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.SEQUENCE;
    }

    @Override
    public boolean terminated() {
        return agentCursor >= agents.size();
    }

    @Override
    public Map<String, Object> executionState() {
        // Save cursor - 1: the agent that was just scheduled for execution.
        // On recovery, firstAction() delegates to nextAction() which calls agents.get(agentCursor++),
        // so the restored cursor must point to the agent that needs to be (re-)executed.
        return agentCursor > 0 ? Map.of("cursor", agentCursor - 1) : Map.of();
    }

    @Override
    public void restoreExecutionState(Map<String, Object> state) {
        Object savedCursor = state.get("cursor");
        if (savedCursor instanceof Number n) {
            this.agentCursor = n.intValue();
        }
    }
}
