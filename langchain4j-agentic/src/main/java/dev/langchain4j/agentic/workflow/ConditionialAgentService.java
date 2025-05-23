package dev.langchain4j.agentic.workflow;

import static dev.langchain4j.agentic.internal.AgentExecutor.agentsToExecutors;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import dev.langchain4j.agentic.Cognisphere;
import dev.langchain4j.agentic.CognisphereOwner;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentInstance;

public class ConditionialAgentService<T> {

    private record ConditionalAgent(Predicate<Cognisphere> condition, List<AgentExecutor> agentExecutors) {}

    private final Class<T> agentServiceClass;

    private List<ConditionalAgent> conditionalAgents = new ArrayList<>();
    private String outputName;

    private ConditionialAgentService(Class<T> agentServiceClass) {
        this.agentServiceClass = agentServiceClass;
    }

    public T build() {
        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentInstance.class, CognisphereOwner.class},
                new ConditionialInvocationHandler());
    }

    private class ConditionialInvocationHandler extends AbstractAgentInvocationHandler {

        public ConditionialInvocationHandler() {
            super(ConditionialAgentService.this.agentServiceClass, ConditionialAgentService.this.outputName);
        }

        public ConditionialInvocationHandler(Cognisphere cognisphere) {
            super(ConditionialAgentService.this.agentServiceClass, ConditionialAgentService.this.outputName, cognisphere);
        }

        @Override
        protected Object doAgentAction(Cognisphere cognisphere) {
            for (ConditionalAgent conditionalAgent : conditionalAgents) {
                if (conditionalAgent.condition.test(cognisphere)) {
                    for (AgentExecutor agentExecutor : conditionalAgent.agentExecutors) {
                        agentExecutor.invoke(cognisphere);
                    }
                }
            }
            return cognisphere.getState();
        }

        @Override
        protected InvocationHandler createHandlerWithCognisphere(Cognisphere cognisphere) {
            return new ConditionialInvocationHandler(cognisphere);
        }
    }

    public static ConditionialAgentService<UntypedAgent> builder() {
        return builder(UntypedAgent.class);
    }

    public static <T> ConditionialAgentService<T> builder(Class<T> agentServiceClass) {
        return new ConditionialAgentService<>(agentServiceClass);
    }

    public ConditionialAgentService<T> subAgents(Predicate<Cognisphere> condition, Object... agents) {
        conditionalAgents.add( new ConditionalAgent(condition,
                agentsToExecutors(Stream.of(agents).map(AgentInstance.class::cast).toList())));
        return this;
    }

    public ConditionialAgentService<T> outputName(String outputName) {
        this.outputName = outputName;
        return this;
    }
}
