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
import java.util.function.Predicate;
import java.util.stream.Stream;

import static dev.langchain4j.agentic.internal.AgentExecutor.agentsToExecutors;

public class LoopAgentService<T> {

    private final Class<T> agentServiceClass;

    private List<AgentExecutor> agentExecutors;
    private int maxIterations = Integer.MAX_VALUE;
    private Predicate<Cognisphere> exitCondition = state -> false;
    private String outputName;

    private LoopAgentService(Class<T> agentServiceClass) {
        this.agentServiceClass = agentServiceClass;
    }

    public T build() {
        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentInstance.class, CognisphereOwner.class},
                new LoopInvocationHandler());
    }

    public class LoopInvocationHandler extends AbstractAgentInvocationHandler {

        public LoopInvocationHandler() {
            super(LoopAgentService.this.agentServiceClass, LoopAgentService.this.outputName);
        }

        public LoopInvocationHandler(Cognisphere cognisphere) {
            super(LoopAgentService.this.agentServiceClass, LoopAgentService.this.outputName, cognisphere);
        }

        @Override
        protected Object doAgentAction(Cognisphere cognisphere) {
            for (int i = 0; i < maxIterations; i++) {
                for (AgentExecutor agentExecutor : agentExecutors) {
                    agentExecutor.invoke(cognisphere);
                    if (exitCondition.test(cognisphere)) {
                        return cognisphere.getState();
                    }
                }
            }
            return cognisphere.getState();
        }

        @Override
        protected InvocationHandler createHandlerWithCognisphere(Cognisphere cognisphere) {
            return new LoopInvocationHandler(cognisphere);
        }
    }

    public static LoopAgentService<UntypedAgent> builder() {
        return builder(UntypedAgent.class);
    }

    public static <T> LoopAgentService<T> builder(Class<T> agentServiceClass) {
        return new LoopAgentService<>(agentServiceClass);
    }

    public LoopAgentService<T> subAgents(Object... agents) {
        this.agentExecutors = agentsToExecutors(Stream.of(agents).map(AgentInstance.class::cast).toList());
        return this;
    }

    public LoopAgentService<T> maxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    public LoopAgentService<T> exitCondition(Predicate<Cognisphere> exitCondition) {
        this.exitCondition = exitCondition;
        return this;
    }

    public LoopAgentService<T> outputName(String outputName) {
        this.outputName = outputName;
        return this;
    }
}
