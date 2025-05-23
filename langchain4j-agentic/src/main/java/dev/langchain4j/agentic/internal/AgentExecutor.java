package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.Cognisphere;
import dev.langchain4j.agentic.CognisphereOwner;
import dev.langchain4j.service.UserMessage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.langchain4j.internal.Utils.getAnnotatedMethod;

public record AgentExecutor(AgentSpecification agentSpecification, AgentInstance agent) {

    public String agentName() {
        return agentSpecification.name();
    }

    public Object invoke(Cognisphere cognisphere) {
        Object invokedAgent = agent instanceof CognisphereOwner co ? co.withCognisphere(cognisphere) : agent;
        Object[] args = agentSpecification.toInvocationArguments(cognisphere);

        Object response = agentSpecification.invoke(invokedAgent, args);
        String outputName = agent.outputName();
        if (outputName != null) {
            cognisphere.writeState(outputName, response);
        }
        if (!(agent instanceof CognisphereOwner)) {
            cognisphere.registerAgentCall(agentSpecification, invokedAgent, args, response);
        }
        return response;
    }

    public static List<AgentExecutor> agentsToExecutors(List<AgentInstance> agents) {
        List<AgentExecutor> agentExecutors = new ArrayList<>();
        for (AgentInstance agent : agents) {
            for (Method method : agent.getClass().getDeclaredMethods()) {
                if (agent instanceof A2AClientInstance a2a) {
                    methodToA2AExecutor(a2a, method).ifPresent(agentExecutors::add);
                } else {
                    methodToAgentExecutor(agent, method).ifPresent(agentExecutors::add);
                }
            }
        }
        return agentExecutors;
    }

    private static Optional<AgentExecutor> methodToA2AExecutor(A2AClientInstance a2aClient, Method method) {
        return getAgentMethod(method)
                .map(agentMethod -> new AgentExecutor(new A2AClientAgentSpecification(a2aClient, agentMethod), a2aClient));
    }

    private static Optional<AgentExecutor> methodToAgentExecutor(AgentInstance agent, Method method) {
        return getAgentMethod(method)
                .map(agentMethod -> new AgentExecutor(AgentSpecification.fromMethod(agentMethod), agent));
    }

    private static Optional<Method> getAgentMethod(Method method) {
        return getAnnotatedMethod(method, Agent.class)
                .or(() -> getAnnotatedMethod(method, UserMessage.class));
    }
}
