package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AbstractService;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentSpecification;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.workflow.ConditionalAgentService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static dev.langchain4j.agentic.internal.AgentUtil.agentsToExecutors;

public class ConditionalAgentServiceImpl<T> extends AbstractService<T, ConditionalAgentService<T>> implements ConditionalAgentService<T> {

    private record ConditionalAgent(Predicate<AgenticScope> condition, List<AgentExecutor> agentExecutors) {}

    private final List<ConditionalAgent> conditionalAgents = new ArrayList<>();

    private ConditionalAgentServiceImpl(Class<T> agentServiceClass) {
        super(agentServiceClass);
    }

    @Override
    public T build() {
        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentSpecification.class, AgenticScopeOwner.class},
                new ConditionialInvocationHandler());
    }

    private class ConditionialInvocationHandler extends AbstractAgentInvocationHandler {

        private ConditionialInvocationHandler() {
            super(ConditionalAgentServiceImpl.this);
        }

        private ConditionialInvocationHandler(DefaultAgenticScope agenticScope) {
            super(ConditionalAgentServiceImpl.this, agenticScope);
        }

        @Override
        protected Object doAgentAction(DefaultAgenticScope agenticScope) {
            for (ConditionalAgent conditionalAgent : conditionalAgents) {
                if (conditionalAgent.condition.test(agenticScope)) {
                    for (AgentExecutor agentExecutor : conditionalAgent.agentExecutors) {
                        agentExecutor.execute(agenticScope);
                    }
                }
            }
            return result(agenticScope, output.apply(agenticScope));
        }

        @Override
        protected InvocationHandler createSubAgentWithAgenticScope(DefaultAgenticScope agenticScope) {
            return new ConditionialInvocationHandler(agenticScope);
        }
    }

    public static ConditionalAgentServiceImpl<UntypedAgent> builder() {
        return builder(UntypedAgent.class);
    }

    public static <T> ConditionalAgentServiceImpl<T> builder(Class<T> agentServiceClass) {
        return new ConditionalAgentServiceImpl<>(agentServiceClass);
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
        conditionalAgents.add(new ConditionalAgent(condition, agentExecutors));
        return this;
    }

    @Override
    public ConditionalAgentServiceImpl<T> subAgent(Predicate<AgenticScope> condition, AgentExecutor agentExecutor) {
        conditionalAgents.add(new ConditionalAgent(condition, List.of(agentExecutor)));
        return this;
    }
}
