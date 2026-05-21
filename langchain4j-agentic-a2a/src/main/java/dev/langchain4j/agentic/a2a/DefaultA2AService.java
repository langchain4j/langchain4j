package dev.langchain4j.agentic.a2a;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.internal.A2AClientBuilder;
import dev.langchain4j.agentic.internal.A2AService;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.InternalAgent;
import java.lang.reflect.Method;
import java.util.Optional;

import static dev.langchain4j.internal.Utils.getAnnotatedMethod;

public class DefaultA2AService implements A2AService {

    @Override
    public <T> A2AClientBuilder<T> a2aBuilder(String a2aServerUrl, Class<T> agentServiceClass) {
        return new DefaultA2AClientBuilder<>(a2aServerUrl, agentServiceClass);
    }

    @Override
    public Optional<AgentExecutor> methodToAgentExecutor(InternalAgent agent, Method method) {
        if (agent instanceof A2AClientInstance a2aAgent) {
            Optional<AgentExecutor> a2aAgentExecutor = getAnnotatedMethod(method, Agent.class)
                    .map(agentMethod -> new AgentExecutor(new A2AClientAgentInvoker(a2aAgent, agentMethod), a2aAgent));
            if (a2aAgentExecutor.isEmpty()) {
                a2aAgentExecutor = getAnnotatedMethod(method, A2AClientAgent.class)
                        .map(agentMethod -> new AgentExecutor(new A2AClientAgentInvoker(a2aAgent, agentMethod), a2aAgent));
            }
            return a2aAgentExecutor;
        }
        return Optional.empty();
    }
}
