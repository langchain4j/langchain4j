package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.cognisphere.DefaultCognisphere;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AbstractService;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentSpecification;
import dev.langchain4j.agentic.internal.CognisphereOwner;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.function.Predicate;

public class LoopAgentServiceImpl<T> extends AbstractService<T, LoopAgentService<T>> implements LoopAgentService<T> {

    private int maxIterations = Integer.MAX_VALUE;
    private Predicate<Cognisphere> exitCondition = state -> false;

    private LoopAgentServiceImpl(Class<T> agentServiceClass) {
        super(agentServiceClass);
    }

    @Override
    public T build() {
        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentSpecification.class, CognisphereOwner.class},
                new LoopInvocationHandler());
    }

    public class LoopInvocationHandler extends AbstractAgentInvocationHandler {

        private LoopInvocationHandler() {
            super(LoopAgentServiceImpl.this);
        }

        private LoopInvocationHandler(DefaultCognisphere cognisphere) {
            super(LoopAgentServiceImpl.this, cognisphere);
        }

        @Override
        protected Object doAgentAction(DefaultCognisphere cognisphere) {
            for (int i = 0; i < maxIterations; i++) {
                for (AgentExecutor agentExecutor : agentExecutors()) {
                    agentExecutor.execute(cognisphere);
                    if (exitCondition.test(cognisphere)) {
                        return cognisphere.state();
                    }
                }
            }
            return result(cognisphere, output.apply(cognisphere));
        }

        @Override
        protected InvocationHandler createSubAgentWithCognisphere(DefaultCognisphere cognisphere) {
            return new LoopInvocationHandler(cognisphere);
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
    public LoopAgentServiceImpl<T> exitCondition(Predicate<Cognisphere> exitCondition) {
        this.exitCondition = exitCondition;
        return this;
    }
}
