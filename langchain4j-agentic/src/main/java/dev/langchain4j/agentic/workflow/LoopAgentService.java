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
import java.util.function.Predicate;

import static dev.langchain4j.agentic.internal.AgentUtil.agentsToExecutors;

public class LoopAgentService<T> implements OutputtingService<LoopAgentService<T>> {

    private final Class<T> agentServiceClass;

    private final List<AgentExecutor> agentExecutors = new ArrayList<>();

    private int maxIterations = Integer.MAX_VALUE;
    private Predicate<Cognisphere> exitCondition = state -> false;
    private String outputName;
    private Function<Cognisphere, Object> output = cognisphere -> null;

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

        private LoopInvocationHandler() {
            super(LoopAgentService.this.agentServiceClass, LoopAgentService.this.outputName);
        }

        private LoopInvocationHandler(Cognisphere cognisphere) {
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
            return result(cognisphere, output.apply(cognisphere));
        }

        @Override
        protected CognisphereOwner createSubAgentWithCognisphere(Cognisphere cognisphere) {
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
        return subAgents(agentsToExecutors(agents));
    }

    public LoopAgentService<T> subAgents(List<AgentExecutor> agentExecutors) {
        this.agentExecutors.addAll(agentExecutors);
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

    @Override
    public LoopAgentService<T> outputName(String outputName) {
        this.outputName = outputName;
        return this;
    }

    @Override
    public LoopAgentService<T> output(Function<Cognisphere, Object> output) {
        this.output = output;
        return this;
    }
}
