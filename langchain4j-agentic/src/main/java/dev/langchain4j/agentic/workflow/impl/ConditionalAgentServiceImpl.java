package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.cognisphere.DefaultCognisphere;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AbstractService;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentInstance;
import dev.langchain4j.agentic.internal.CognisphereOwner;
import dev.langchain4j.agentic.workflow.ConditionalAgentService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static dev.langchain4j.agentic.internal.AgentUtil.agentsToExecutors;

public class ConditionalAgentServiceImpl<T> extends AbstractService<T, ConditionalAgentService<T>> implements ConditionalAgentService<T> {

    private record ConditionalAgent(Predicate<Cognisphere> condition, List<AgentExecutor> agentExecutors) {}

    private final List<ConditionalAgent> conditionalAgents = new ArrayList<>();

    private ConditionalAgentServiceImpl(Class<T> agentServiceClass) {
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
            super(ConditionalAgentServiceImpl.this);
        }

        private ConditionialInvocationHandler(DefaultCognisphere cognisphere) {
            super(ConditionalAgentServiceImpl.this, cognisphere);
        }

        @Override
        protected Object doAgentAction(DefaultCognisphere cognisphere) {
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
        protected InvocationHandler createSubAgentWithCognisphere(DefaultCognisphere cognisphere) {
            return new ConditionialInvocationHandler(cognisphere);
        }
    }

    public static ConditionalAgentServiceImpl<UntypedAgent> builder() {
        return builder(UntypedAgent.class);
    }

    public static <T> ConditionalAgentServiceImpl<T> builder(Class<T> agentServiceClass) {
        return new ConditionalAgentServiceImpl<>(agentServiceClass);
    }

    @Override
    public ConditionalAgentServiceImpl<T> subAgents(Object... agents) {
        return subAgents(cognisphere -> true, agents);
    }

    @Override
    public ConditionalAgentServiceImpl<T> subAgents(Predicate<Cognisphere> condition, Object... agents) {
        return subAgents(condition, agentsToExecutors(agents));
    }

    @Override
    public ConditionalAgentServiceImpl<T> subAgents(List<AgentExecutor> agentExecutors) {
        return subAgents(cognisphere -> true, agentExecutors);
    }

    @Override
    public ConditionalAgentServiceImpl<T> subAgents(Predicate<Cognisphere> condition, List<AgentExecutor> agentExecutors) {
        conditionalAgents.add(new ConditionalAgent(condition, agentExecutors));
        return this;
    }

    @Override
    public ConditionalAgentServiceImpl<T> subAgent(Predicate<Cognisphere> condition, AgentExecutor agentExecutor) {
        conditionalAgents.add(new ConditionalAgent(condition, List.of(agentExecutor)));
        return this;
    }
}
