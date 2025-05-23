package dev.langchain4j.agentic.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.agentsToExecutors;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.internal.CognisphereOwner;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentInstance;

public class ConditionialAgentService<T> implements OutputtingService<ConditionialAgentService<T>> {

    private record ConditionalAgent(Predicate<Cognisphere> condition, List<AgentExecutor> agentExecutors) {}

    private final Class<T> agentServiceClass;

    private List<ConditionalAgent> conditionalAgents = new ArrayList<>();
    private String outputName;
    private Function<Cognisphere, Object> output = cognisphere -> null;

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

        private ConditionialInvocationHandler() {
            super(ConditionialAgentService.this.agentServiceClass, ConditionialAgentService.this.outputName);
        }

        private ConditionialInvocationHandler(Cognisphere cognisphere) {
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
            return result(cognisphere, output.apply(cognisphere));
        }

        @Override
        protected CognisphereOwner createSubAgentWithCognisphere(Cognisphere cognisphere) {
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
        return subAgents(condition, agentsToExecutors(agents));
    }

    public ConditionialAgentService<T> subAgents(Predicate<Cognisphere> condition, List<AgentExecutor> agentExecutors) {
        conditionalAgents.add(new ConditionalAgent(condition, agentExecutors));
        return this;
    }

    public ConditionialAgentService<T> subAgent(Predicate<Cognisphere> condition, AgentExecutor agentExecutor) {
        conditionalAgents.add(new ConditionalAgent(condition, List.of(agentExecutor)));
        return this;
    }

    @Override
    public ConditionialAgentService<T> outputName(String outputName) {
        this.outputName = outputName;
        return this;
    }

    @Override
    public ConditionialAgentService<T> output(Function<Cognisphere, Object> output) {
        this.output = output;
        return this;
    }
}
