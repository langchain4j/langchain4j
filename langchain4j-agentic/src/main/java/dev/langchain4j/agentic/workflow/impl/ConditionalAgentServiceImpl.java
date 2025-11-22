package dev.langchain4j.agentic.workflow.impl;

import static dev.langchain4j.agentic.internal.AgentUtil.agentsToExecutors;
import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AbstractServiceBuilder;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.workflow.ConditionalAgentService;
import dev.langchain4j.agentic.workflow.impl.ConditionalPlanner.ConditionalAgent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class ConditionalAgentServiceImpl<T> extends AbstractServiceBuilder<T, ConditionalAgentService<T>>
        implements ConditionalAgentService<T> {

    private final List<ConditionalAgent> conditionalAgents = new ArrayList<>();

    public ConditionalAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    @Override
    public T build() {
        return build(new ConditionalSupplier(conditionalAgents));
    }

    public static ConditionalAgentServiceImpl<UntypedAgent> builder() {
        return new ConditionalAgentServiceImpl<>(UntypedAgent.class, null);
    }

    public static <T> ConditionalAgentServiceImpl<T> builder(Class<T> agentServiceClass) {
        return new ConditionalAgentServiceImpl<>(agentServiceClass, validateAgentClass(agentServiceClass, false));
    }

    @Override
    public ConditionalAgentServiceImpl<T> subAgents(Object... agents) {
        return subAgents(agenticScope -> true, agents);
    }

    @Override
    public ConditionalAgentServiceImpl<T> subAgents(Predicate<AgenticScope> condition, Object... agents) {
        return subAgents(condition, agentsToExecutors(agents));
    }

    @Override
    public ConditionalAgentServiceImpl<T> subAgents(List<AgentExecutor> agentExecutors) {
        return subAgents(agenticScope -> true, agentExecutors);
    }

    @Override
    public ConditionalAgentServiceImpl<T> subAgents(Predicate<AgenticScope> condition, List<AgentExecutor> agentExecutors) {
        super.subAgents(agentExecutors);
        conditionalAgents.add(new ConditionalAgent(condition, agentExecutors.stream().map(AgentInstance.class::cast).toList()));
        return this;
    }

    @Override
    public ConditionalAgentServiceImpl<T> subAgent(Predicate<AgenticScope> condition, AgentExecutor agentExecutor) {
        conditionalAgents.add(new ConditionalAgent(condition, List.of(agentExecutor)));
        return this;
    }

    @Override
    public String serviceType() {
        return "Conditional";
    }
}
