package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AbstractService;
import dev.langchain4j.agentic.internal.AgentSpecification;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

public class SequentialAgentServiceImpl<T> extends AbstractService<T, SequentialAgentService<T>> implements SequentialAgentService<T> {

    private SequentialAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    @Override
    public T build() {
        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentSpecification.class, AgenticScopeOwner.class},
                new SequentialInvocationHandler());
    }

    private class SequentialInvocationHandler extends AbstractAgentInvocationHandler {

        private SequentialInvocationHandler() {
            super(SequentialAgentServiceImpl.this);
        }

        private SequentialInvocationHandler(DefaultAgenticScope agenticScope) {
            super(SequentialAgentServiceImpl.this, agenticScope);
        }

        @Override
        protected Object doAgentAction(DefaultAgenticScope agenticScope) {
            agentExecutors().forEach(agentExecutor -> agentExecutor.execute(agenticScope));
            return result(agenticScope, output.apply(agenticScope));
        }

        @Override
        protected InvocationHandler createSubAgentWithAgenticScope(DefaultAgenticScope agenticScope) {
            return new SequentialInvocationHandler(agenticScope);
        }
    }

    public static SequentialAgentServiceImpl<UntypedAgent> builder() {
        return new SequentialAgentServiceImpl<>(UntypedAgent.class, null);
    }

    public static <T> SequentialAgentServiceImpl<T> builder(Class<T> agentServiceClass) {
        return new SequentialAgentServiceImpl<>(agentServiceClass, validateAgentClass(agentServiceClass, false));
    }
}
