package dev.langchain4j.agentic;

import io.a2a.spec.A2A;
import io.a2a.spec.A2AServerException;

public class AgentServices {

    private AgentServices() { }

    public static <T> AgentBuilder<T> builder(Class<T> agentServiceClass) {
        return new AgentBuilder<>(agentServiceClass);
    }

    public static A2AClientBuilder<UntypedAgent> a2aBuilder(String a2aServerUrl) {
        return a2aBuilder(a2aServerUrl, UntypedAgent.class);
    }

    public static <T> A2AClientBuilder<T> a2aBuilder(String a2aServerUrl, Class<T> agentServiceClass) {
        try {
            return new A2AClientBuilder(A2A.getAgentCard(a2aServerUrl), agentServiceClass);
        } catch (A2AServerException e) {
            throw new RuntimeException(e);
        }
    }
}
