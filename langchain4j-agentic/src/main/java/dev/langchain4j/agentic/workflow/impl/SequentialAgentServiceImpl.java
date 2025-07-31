package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AbstractService;
import dev.langchain4j.agentic.internal.AgentInstance;
import dev.langchain4j.agentic.internal.CognisphereOwner;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import java.lang.reflect.Proxy;

public class SequentialAgentServiceImpl<T> extends AbstractService<T, SequentialAgentService<T>> implements SequentialAgentService<T> {

    private SequentialAgentServiceImpl(Class<T> agentServiceClass) {
        super(agentServiceClass);
    }

    @Override
    public T build() {
        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentInstance.class, CognisphereOwner.class},
                new SequentialInvocationHandler());
    }

    private class SequentialInvocationHandler extends AbstractAgentInvocationHandler {

        private SequentialInvocationHandler() {
            super(SequentialAgentServiceImpl.this);
        }

        private SequentialInvocationHandler(Cognisphere cognisphere) {
            super(SequentialAgentServiceImpl.this, cognisphere);
        }

        @Override
        protected Object doAgentAction(Cognisphere cognisphere) {
            agentExecutors().forEach(agentExecutor -> agentExecutor.invoke(cognisphere));
            return result(cognisphere, output.apply(cognisphere));
        }

        @Override
        protected CognisphereOwner createSubAgentWithCognisphere(Cognisphere cognisphere) {
            return new SequentialInvocationHandler(cognisphere);
        }
    }

    public static SequentialAgentServiceImpl<UntypedAgent> builder() {
        return builder(UntypedAgent.class);
    }

    public static <T> SequentialAgentServiceImpl<T> builder(Class<T> agentServiceClass) {
        return new SequentialAgentServiceImpl<>(agentServiceClass);
    }
}
