package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.MapperAgentInvoker;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ParallelMapperPlanner implements Planner {

    private final String itemsProvider;
    private final boolean isArrayResult;
    private final Class<?> arrayClass;

    private AgentExecutor subagent;
    private String mapperAgentId;
    private String outputKey;
    private List<String> instanceOutputKeys;
    private int itemCount;
    private final AtomicInteger completedCount = new AtomicInteger();

    public ParallelMapperPlanner(String itemsProvider, boolean isArrayResult, Class<? extends Object[]> arrayclass) {
        this(itemsProvider, isArrayResult ? arrayclass : null);
    }

    /**
     * @param itemsProvider the name of the state holding the items to map
     * @param arrayClass the type of the array to return the results in, which can be an array of a primitive type,
     *        like {@code int[]}, or {@code null} to return them in a list
     */
    public ParallelMapperPlanner(String itemsProvider, Class<?> arrayClass) {
        this.itemsProvider = itemsProvider;
        this.isArrayResult = arrayClass != null;
        this.arrayClass = arrayClass;
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        this.subagent = (AgentExecutor) initPlanningContext.subagents().get(0);
        this.mapperAgentId = initPlanningContext.plannerAgent().agentId();
        this.outputKey = initPlanningContext.plannerAgent().outputKey();
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        Object collectionObj = planningContext.agenticScope().readState(itemsProvider);
        if (collectionObj == null) {
            return done();
        }

        List<?> items = collectItems(collectionObj);

        if (items.isEmpty()) {
            return doneWithResults(planningContext, List.of());
        }

        this.itemCount = items.size();

        List<AgentInstance> instances = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            MapperAgentInvoker instanceInvoker =
                    new MapperAgentInvoker(subagent.agentInvoker(), item, i, mapperAgentId);
            instances.add(new AgentExecutor(instanceInvoker, subagent.agent()));
        }
        this.instanceOutputKeys =
                instances.stream().map(AgentInstance::outputKey).toList();

        return call(instances);
    }

    private List<?> collectItems(Object collectionObj) {
        List<?> items;
        if (collectionObj instanceof List<?> list) {
            items = list;
        } else if (collectionObj instanceof Collection<?> collection) {
            items = new ArrayList<>(collection);
        } else if (collectionObj.getClass().isArray()) {
            // read through reflection, since an array of a primitive type, like int[], is not an Object[]
            int length = Array.getLength(collectionObj);
            List<Object> elements = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                elements.add(Array.get(collectionObj, i));
            }
            items = elements;
        } else {
            throw new IllegalArgumentException(
                    "The value for itemsProvider '" + itemsProvider + "' must be a Collection or array, but was: "
                            + collectionObj.getClass().getName());
        }
        return items;
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        if (completedCount.incrementAndGet() >= itemCount) {
            List<Object> results = new ArrayList<>(itemCount);
            for (int i = 0; i < itemCount; i++) {
                String instanceOutputKey = instanceOutputKeys.get(i);
                results.add(planningContext.agenticScope().readState(instanceOutputKey));
                planningContext.agenticScope().writeState(instanceOutputKey, null);
            }
            return doneWithResults(planningContext, results);
        }
        return done();
    }

    private Action doneWithResults(PlanningContext planningContext, List<Object> results) {
        Object result = isArrayResult ? toArray(results) : results;
        if (outputKey != null && !outputKey.isBlank()) {
            planningContext.agenticScope().writeState(outputKey, result);
        }
        return done(result);
    }

    private Object toArray(List<Object> results) {
        // filled through reflection, which unboxes each result when the array is of a primitive type, like int[]
        Object array = Array.newInstance(arrayClass.getComponentType(), results.size());
        for (int i = 0; i < results.size(); i++) {
            Object result = results.get(i);
            // a null result keeps the default value of the component type: 0 or false for a primitive, null otherwise
            if (result != null) {
                Array.set(array, i, result);
            }
        }
        return array;
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.PARALLEL;
    }
}
