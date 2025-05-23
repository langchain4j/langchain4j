package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AgentInstance;
import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.internal.CognisphereOwner;
import dev.langchain4j.agentic.internal.AgentExecutor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static dev.langchain4j.agentic.internal.AgentUtil.agentsToExecutors;

public class SequentialAgentService<T> implements OutputtingService<SequentialAgentService<T>> {

    private final Class<T> agentServiceClass;

    private final List<AgentExecutor> agentExecutors = new ArrayList<>();

    private String outputName;
    private Function<Cognisphere, Object> output = cognisphere -> null;

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

        private SequentialInvocationHandler() {
            super(SequentialAgentService.this.agentServiceClass, SequentialAgentService.this.outputName);
        }

        private SequentialInvocationHandler(Cognisphere cognisphere) {
            super(SequentialAgentService.this.agentServiceClass, SequentialAgentService.this.outputName, cognisphere);
        }

        @Override
        protected Object doAgentAction(Cognisphere cognisphere) {
            agentExecutors.forEach(agentExecutor -> agentExecutor.invoke(cognisphere));
            return result(cognisphere, output.apply(cognisphere));
        }

        @Override
        protected CognisphereOwner createSubAgentWithCognisphere(Cognisphere cognisphere) {
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
        return subAgents(agentsToExecutors(agents));
    }

    public SequentialAgentService<T> subAgents(List<AgentExecutor> agentExecutors) {
        this.agentExecutors.addAll(agentExecutors);
        return this;
    }

    @Override
    public SequentialAgentService<T> outputName(String outputName) {
        this.outputName = outputName;
        return this;
    }

    @Override
    public SequentialAgentService<T> output(Function<Cognisphere, Object> output) {
        this.output = output;
        return this;
    }
}
