package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AbstractService;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentInstance;
import dev.langchain4j.agentic.internal.CognisphereOwner;
import dev.langchain4j.agentic.workflow.ConditionialAgentService;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static dev.langchain4j.agentic.internal.AgentUtil.agentsToExecutors;

public class ConditionialAgentServiceImpl<T> extends AbstractService<T, ConditionialAgentService<T>> implements ConditionialAgentService<T> {

    private record ConditionalAgent(Predicate<Cognisphere> condition, List<AgentExecutor> agentExecutors) {}

    private List<ConditionalAgent> conditionalAgents = new ArrayList<>();

    private ConditionialAgentServiceImpl(Class<T> agentServiceClass) {
        super(agentServiceClass);
    }

    @Override
    public T build() {
        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentInstance.class, CognisphereOwner.class},
                new ConditionialInvocationHandler());
    }

    private class ConditionialInvocationHandler extends AbstractAgentInvocationHandler {

        private ConditionialInvocationHandler() {
            super(ConditionialAgentServiceImpl.this);
        }

        private ConditionialInvocationHandler(Cognisphere cognisphere) {
            super(ConditionialAgentServiceImpl.this, cognisphere);
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

    public static ConditionialAgentServiceImpl<UntypedAgent> builder() {
        return builder(UntypedAgent.class);
    }

    public static <T> ConditionialAgentServiceImpl<T> builder(Class<T> agentServiceClass) {
        return new ConditionialAgentServiceImpl<>(agentServiceClass);
    }

    @Override
    public ConditionialAgentServiceImpl<T> subAgents(Object... agents) {
        return subAgents(cognisphere -> true, agents);
    }

    @Override
    public ConditionialAgentServiceImpl<T> subAgents(Predicate<Cognisphere> condition, Object... agents) {
        return subAgents(condition, agentsToExecutors(agents));
    }

    @Override
    public ConditionialAgentServiceImpl<T> subAgents(List<AgentExecutor> agentExecutors) {
        return subAgents(cognisphere -> true, agentExecutors);
    }

    @Override
    public ConditionialAgentServiceImpl<T> subAgents(Predicate<Cognisphere> condition, List<AgentExecutor> agentExecutors) {
        conditionalAgents.add(new ConditionalAgent(condition, agentExecutors));
        return this;
    }

    @Override
    public ConditionialAgentServiceImpl<T> subAgent(Predicate<Cognisphere> condition, AgentExecutor agentExecutor) {
        conditionalAgents.add(new ConditionalAgent(condition, List.of(agentExecutor)));
        return this;
    }
}
