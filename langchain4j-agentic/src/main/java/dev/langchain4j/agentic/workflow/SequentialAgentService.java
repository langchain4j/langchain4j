package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AgentInstance;
import dev.langchain4j.agentic.Cognisphere;
import dev.langchain4j.agentic.CognisphereOwner;
import dev.langchain4j.agentic.internal.AgentExecutor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.agentic.internal.AgentExecutor.agentsToExecutors;

public class SequentialAgentService<T> {

    private final Class<T> agentServiceClass;

    private List<AgentExecutor> agentExecutors;
    private String outputName;

    private SequentialAgentService(Class<T> agentServiceClass) {
        this.agentServiceClass = agentServiceClass;
    }

    public T build() {
        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentInstance.class, CognisphereOwner.class},
                new SequentialInvocationHandler());
    }

    private class SequentialInvocationHandler extends AbstractAgentInvocationHandler {

        public SequentialInvocationHandler() {
            super(SequentialAgentService.this.agentServiceClass, SequentialAgentService.this.outputName);
        }

        public SequentialInvocationHandler(Cognisphere cognisphere) {
            super(SequentialAgentService.this.agentServiceClass, SequentialAgentService.this.outputName, cognisphere);
        }

        @Override
        protected Object doAgentAction(Cognisphere cognisphere) {
            for (AgentExecutor agentExecutor : agentExecutors) {
                agentExecutor.invoke(cognisphere);
            }
            return cognisphere.getState();
        }

        @Override
        protected InvocationHandler createHandlerWithCognisphere(Cognisphere cognisphere) {
            return new SequentialInvocationHandler(cognisphere);
        }
    }

    public static SequentialAgentService<UntypedAgent> builder() {
        return builder(UntypedAgent.class);
    }

    public static <T> SequentialAgentService<T> builder(Class<T> agentServiceClass) {
        return new SequentialAgentService<>(agentServiceClass);
    }

    public SequentialAgentService<T> subAgents(Object... agents) {
        this.agentExecutors = agentsToExecutors(Stream.of(agents).map(AgentInstance.class::cast).toList());
        return this;
    }

    public SequentialAgentService<T> outputName(String outputName) {
        this.outputName = outputName;
        return this;
    }
}
