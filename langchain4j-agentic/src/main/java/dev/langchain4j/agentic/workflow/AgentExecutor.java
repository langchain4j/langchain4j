package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgentInstance;
import dev.langchain4j.agentic.AgentSpecification;
import dev.langchain4j.service.UserMessage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getAnnotatedMethod;

record AgentExecutor(AgentSpecification agentSpecification, AgentInstance agent) {

    boolean isWorkflowAgent() {
        return agentSpecification.isWorkflowAgent();
    }

    Object invoke(Map<String, Object> state) {
        try {
            Object response = agentSpecification.method().invoke(agent, agentSpecification.toInvocationArguments(state));
            if (agent.outputName() != null) {
                state.put(agent.outputName(), response);
            }
            return response;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    static List<AgentExecutor> agentsToExecutors(List<AgentInstance> agents) {
        List<AgentExecutor> agentExecutors = new ArrayList<>();
        for (AgentInstance agent : agents) {
            for (Method method : agent.getClass().getDeclaredMethods()) {
                getAnnotatedMethod(method, Agent.class)
                        .or(() -> getAnnotatedMethod(method, UserMessage.class))
                        .ifPresent(agentMethod -> agentExecutors.add(new AgentExecutor(AgentSpecification.fromMethod(agentMethod), agent)) );
            }
        }
        return agentExecutors;
    }
}
