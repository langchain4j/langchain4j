package dev.langchain4j.agentic.patterns.htn;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static dev.langchain4j.agentic.patterns.htn.DecompositionMethod.decompose;

public sealed interface TaskNode {

    String name();

    static TaskNode primitive(Class<?> agentType) {
        return new PrimitiveTask(agentType, null, null, null);
    }

    static List<TaskNode> primitives(Class<?>... agentTypes) {
        return Arrays.stream(agentTypes).map(TaskNode::primitive).toList();
    }

    static TaskNode primitive(Class<?> agentType,
                              Consumer<AgenticScope> effect) {
        return new PrimitiveTask(agentType, null, null, effect);
    }

    static TaskNode primitive(Class<?> agentType,
                              Predicate<AgenticScope> precondition,
                              Consumer<AgenticScope> effect) {
        return new PrimitiveTask(agentType, null, precondition, effect);
    }

    static TaskNode primitive(AgentInstance agent) {
        return new PrimitiveTask(agent.type(), agent, null, null);
    }

    static List<TaskNode> primitives(AgentInstance... agents) {
        return Arrays.stream(agents).map(TaskNode::primitive).toList();
    }

    static TaskNode primitive(AgentInstance agent,
                              Consumer<AgenticScope> effect) {
        return new PrimitiveTask(agent.type(), agent, null, effect);
    }

    static TaskNode primitive(AgentInstance agent,
                              Predicate<AgenticScope> precondition,
                              Consumer<AgenticScope> effect) {
        return new PrimitiveTask(agent.type(), agent, precondition, effect);
    }

    static TaskNode compound(String name, TaskNode... children) {
        return new CompoundTask(name, decompose(children));
    }

    static TaskNode compound(String name, List<TaskNode> children) {
        return new CompoundTask(name, decompose(children));
    }

    static TaskNode compound(String name, DecompositionStrategy strategy) {
        return new CompoundTask(name, new DecompositionMethod(s -> true, strategy));
    }

    static TaskNode compound(String name, DecompositionMethod... methods) {
        return new CompoundTask(name, methods);
    }

    record PrimitiveTask(Class<?> agentType, AgentInstance agentInstance,
                         Predicate<AgenticScope> precondition,
                         Consumer<AgenticScope> effect) implements TaskNode {
        @Override
        public String name() {
            return agentInstance != null ? agentInstance.name() : agentType.getSimpleName();
        }
    }

    record CompoundTask(String name, DecompositionMethod... methods) implements TaskNode {}
}
