package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.MultiInstanceAgentInvoker;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ParallelMultiInstancePlanner implements Planner {

    private final String itemsProvider;
    private AgentExecutor subagent;
    private String resultKeyPrefix;
    private int itemCount;
    private final AtomicInteger completedCount = new AtomicInteger();

    public ParallelMultiInstancePlanner(String itemsProvider) {
        this.itemsProvider = itemsProvider;
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        this.subagent = (AgentExecutor) initPlanningContext.subagents().get(0);
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        Object collectionObj = planningContext.agenticScope().readState(itemsProvider);
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
                    "The value for itemsProvider '" + itemsProvider + "' must be a Collection or array, but was: "
                            + collectionObj.getClass().getName());
        }

        if (items.isEmpty()) {
            return done();
        }

        this.itemCount = items.size();
        this.resultKeyPrefix = subagent.agentInvoker().outputKey();

        List<AgentInstance> instances = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            MultiInstanceAgentInvoker instanceInvoker = new MultiInstanceAgentInvoker(subagent.agentInvoker(), item, i);
            instances.add(new AgentExecutor(instanceInvoker, subagent.agent()));
        }

        return call(instances);
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        if (completedCount.incrementAndGet() >= itemCount) {
            List<Object> results = new ArrayList<>(itemCount);
            for (int i = 0; i < itemCount; i++) {
                results.add(planningContext.agenticScope().readState(resultKeyPrefix + "_" + i));
            }
            return done(results);
        }
        return done();
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.PARALLEL;
    }
}
