package dev.langchain4j.agentic.workflow.impl;

import static dev.langchain4j.agentic.internal.AgentUtil.hasStreamingAgent;
import static dev.langchain4j.agentic.internal.AgentUtil.isOnlyLastStreamingAgent;
import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AbstractService;
import dev.langchain4j.agentic.internal.AgentSpecification;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class SequentialAgentServiceImpl<T> extends AbstractService<T, SequentialAgentService<T>>
        implements SequentialAgentService<T> {

    private SequentialAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    @Override
    public T build() {
        checkSubAgents();
        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentSpecification.class, AgenticScopeOwner.class},
                new SequentialInvocationHandler());
    }

    private void checkSubAgents() {
        if (hasStreamingAgent(this.agentExecutors()) && !isOnlyLastStreamingAgent(this.agentExecutors())) {
            throw new IllegalArgumentException("Only the last sub-agent can return TokenStream.");
        }
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
