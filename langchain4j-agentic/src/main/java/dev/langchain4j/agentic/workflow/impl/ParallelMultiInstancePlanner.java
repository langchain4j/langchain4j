package dev.langchain4j.agentic.workflow.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.MultiInstanceAgentInvoker;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.planner.Planner;

public class ParallelMultiInstancePlanner implements Planner {

    private final String inputKey;
    private AgentExecutor subagent;

    public ParallelMultiInstancePlanner(String inputKey) {
        this.inputKey = inputKey;
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        this.subagent = (AgentExecutor) initPlanningContext.subagents().get(0);
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        Object collectionObj = planningContext.agenticScope().readState(inputKey);
        if (collectionObj == null) {
            return done();
        }

        List<?> items;
        if (collectionObj instanceof List<?> list) {
            items = list;
        } else if (collectionObj instanceof Collection<?> collection) {
            items = new ArrayList<>(collection);
        } else if (collectionObj.getClass().isArray()) {
            items = java.util.Arrays.asList((Object[]) collectionObj);
        } else {
            throw new IllegalArgumentException(
                    "The value for inputKey '" + inputKey + "' must be a Collection or array, but was: "
                            + collectionObj.getClass().getName());
        }

        if (items.isEmpty()) {
            return done();
        }

        List<AgentInstance> instances = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            MultiInstanceAgentInvoker instanceInvoker = new MultiInstanceAgentInvoker(
                    subagent.agentInvoker(), item, i);
            instances.add(new AgentExecutor(instanceInvoker, subagent.agent()));
        }

        return call(instances);
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        return done();
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.PARALLEL;
    }
}
