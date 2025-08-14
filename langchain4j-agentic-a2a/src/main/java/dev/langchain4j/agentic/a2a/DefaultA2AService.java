package dev.langchain4j.agentic.a2a;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.internal.A2AClientBuilder;
import dev.langchain4j.agentic.internal.A2AService;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentInvoker;
import dev.langchain4j.agentic.internal.AgentSpecification;
import java.lang.reflect.Method;
import java.util.Optional;

import static dev.langchain4j.internal.Utils.getAnnotatedMethod;

public class DefaultA2AService implements A2AService {

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public <T> A2AClientBuilder<T> a2aBuilder(final String a2aServerUrl, final Class<T> agentServiceClass) {
        return new DefaultA2AClientBuilder<>(a2aServerUrl, agentServiceClass);
    }

    @Override
    public Optional<AgentExecutor> methodToAgentExecutor(final AgentSpecification agent, final Method method) {
        if (agent instanceof A2AClientSpecification a2aAgent) {
            return getAnnotatedMethod(method, Agent.class)
                    .map(agentMethod -> new AgentExecutor(new A2AClientAgentInvoker(a2aAgent, agentMethod), a2aAgent));
        }
        return getAnnotatedMethod(method, Agent.class)
                .map(agentMethod -> new AgentExecutor(AgentInvoker.fromMethod(agent, agentMethod), agent));    }
}
