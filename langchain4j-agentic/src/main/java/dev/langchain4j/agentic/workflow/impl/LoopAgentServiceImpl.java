package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AbstractService;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentSpecification;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.function.Predicate;

public class LoopAgentServiceImpl<T> extends AbstractService<T, LoopAgentService<T>> implements LoopAgentService<T> {

    private int maxIterations = Integer.MAX_VALUE;
    private Predicate<AgenticScope> exitCondition = state -> false;

    private LoopAgentServiceImpl(Class<T> agentServiceClass) {
        super(agentServiceClass);
    }

    @Override
    public T build() {
        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentSpecification.class, AgenticScopeOwner.class},
                new LoopInvocationHandler());
    }

    public class LoopInvocationHandler extends AbstractAgentInvocationHandler {

        private LoopInvocationHandler() {
            super(LoopAgentServiceImpl.this);
        }

        private LoopInvocationHandler(DefaultAgenticScope agenticScope) {
            super(LoopAgentServiceImpl.this, agenticScope);
        }

        @Override
        protected Object doAgentAction(DefaultAgenticScope agenticScope) {
            for (int i = 0; i < maxIterations; i++) {
                for (AgentExecutor agentExecutor : agentExecutors()) {
                    agentExecutor.execute(agenticScope);
                    if (exitCondition.test(agenticScope)) {
                        return agenticScope.state();
                    }
                }
            }
            return result(agenticScope, output.apply(agenticScope));
        }

        @Override
        protected InvocationHandler createSubAgentWithAgenticScope(DefaultAgenticScope agenticScope) {
            return new LoopInvocationHandler(agenticScope);
        }
    }

    public static LoopAgentServiceImpl<UntypedAgent> builder() {
        return builder(UntypedAgent.class);
    }

    public static <T> LoopAgentServiceImpl<T> builder(Class<T> agentServiceClass) {
        return new LoopAgentServiceImpl<>(agentServiceClass);
    }

    @Override
    public LoopAgentServiceImpl<T> maxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    @Override
    public LoopAgentServiceImpl<T> exitCondition(Predicate<AgenticScope> exitCondition) {
        this.exitCondition = exitCondition;
        return this;
    }
}
