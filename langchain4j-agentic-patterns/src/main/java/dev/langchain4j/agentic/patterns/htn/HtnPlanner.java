package dev.langchain4j.agentic.patterns.htn;

import dev.langchain4j.agentic.patterns.htn.TaskNode.CompoundTask;
import dev.langchain4j.agentic.patterns.htn.TaskNode.PrimitiveTask;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HtnPlanner implements Planner {

    private final TaskNode root;

    private Map<Class<?>, AgentInstance> agentsByType;

    private final Set<String> completed = new LinkedHashSet<>();

    public HtnPlanner(TaskNode root) {
        this.root = root;
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        agentsByType = new HashMap<>();
        for (AgentInstance agent : initPlanningContext.subagents()) {
            agentsByType.put(agent.type(), agent);
        }
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        AgenticScope scope = planningContext.agenticScope();
        Deque<TaskNode> stack = new ArrayDeque<>();
        stack.push(root);
        Map<String, Integer> counters = new HashMap<>();

        while (!stack.isEmpty()) {
            TaskNode current = stack.pop();

            if (current instanceof PrimitiveTask p) {
                AgentInstance agent = resolveAgent(p);
                String key = agentKey(agent, counters);

                if (completed.contains(key)) {
                    continue;
                }

                if (p.precondition() != null && !p.precondition().test(scope)) {
                    throw new IllegalStateException(
                            "Planning failed: precondition not met for task '" + p.name() + "'");
                }

                if (p.effect() != null) {
                    p.effect().accept(scope);
                }

                completed.add(key);
                return call(agent);

            } else if (current instanceof CompoundTask c) {
                decomposeCompound(c, stack, scope);
            }
        }

        return done();
    }

    private void decomposeCompound(CompoundTask c, Deque<TaskNode> stack, AgenticScope scope) {
        for (DecompositionMethod m : c.methods()) {
            if (m.guard().test(scope)) {
                List<TaskNode> subtasks = m.decompose(scope, agentsByType);
                for (int j = subtasks.size() - 1; j >= 0; j--) {
                    stack.push(subtasks.get(j));
                }
                return;
            }
        }
        throw new IllegalStateException(
                "No applicable decomposition method for compound task '" + c.name() + "'");
    }

    private static String agentKey(AgentInstance agent, Map<String, Integer> counters) {
        String name = agent.name();
        int index = counters.merge(name, 0, (old, v) -> old + 1);
        return name + "#" + index;
    }

    private AgentInstance resolveAgent(PrimitiveTask p) {
        if (p.agentInstance() != null) {
            return p.agentInstance();
        }
        AgentInstance agent = agentsByType.get(p.agentType());
        if (agent != null) {
            return agent;
        }
        throw new IllegalStateException("No agent found for type '" + p.agentType().getSimpleName()
                + "'. Available agent types: " + agentsByType.keySet());
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.SEQUENCE;
    }

    @Override
    public Map<String, Object> executionState() {
        return Map.of("completed", List.copyOf(completed));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void restoreExecutionState(Map<String, Object> state) {
        List<String> saved = (List<String>) state.get("completed");
        if (saved != null) {
            completed.addAll(saved);
        }
    }
}
