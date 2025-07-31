package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AbstractService;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentInstance;
import dev.langchain4j.agentic.internal.CognisphereOwner;
import dev.langchain4j.agentic.workflow.LoopAgentService;
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
                new Class<?>[] {agentServiceClass, AgentInstance.class, CognisphereOwner.class},
                new LoopInvocationHandler());
    }

    public class LoopInvocationHandler extends AbstractAgentInvocationHandler {

        private LoopInvocationHandler() {
            super(LoopAgentServiceImpl.this);
        }

        private LoopInvocationHandler(Cognisphere cognisphere) {
            super(LoopAgentServiceImpl.this, cognisphere);
        }

        @Override
        protected Object doAgentAction(Cognisphere cognisphere) {
            for (int i = 0; i < maxIterations; i++) {
                for (AgentExecutor agentExecutor : agentExecutors()) {
                    agentExecutor.invoke(cognisphere);
                    if (exitCondition.test(cognisphere)) {
                        return cognisphere.getState();
                    }
                }
            }
            return result(cognisphere, output.apply(cognisphere));
        }

        @Override
        protected CognisphereOwner createSubAgentWithCognisphere(Cognisphere cognisphere) {
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
