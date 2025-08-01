package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.cognisphere.DefaultCognisphere;

public record AgentExecutor(AgentSpecification agentSpecification, Object agent) {

    public String agentName() {
        return agentSpecification.name();
    }

    public Object invoke(DefaultCognisphere cognisphere) {
        Object invokedAgent = agent instanceof CognisphereOwner co ? co.withCognisphere(cognisphere) : agent;
        Object[] args = agentSpecification.toInvocationArguments(cognisphere);

        Object response = agentSpecification.invoke(invokedAgent, args);
        String outputName = agentSpecification.outputName();
        if (outputName != null && !outputName.isBlank()) {
            cognisphere.writeState(outputName, response);
        }
        cognisphere.registerAgentCall(agentSpecification, invokedAgent, args, response);
        return response;
    }
}
