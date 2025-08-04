package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.cognisphere.DefaultCognisphere;

public record AgentExecutor(AgentInvoker agentInvoker, Object agent) {

    public String agentName() {
        return agentInvoker.name();
    }

    public Object execute(DefaultCognisphere cognisphere) {
        Object invokedAgent = agent instanceof CognisphereOwner co ? co.withCognisphere(cognisphere) : agent;
        Object[] args = agentInvoker.toInvocationArguments(cognisphere);

        Object response = agentInvoker.invoke(invokedAgent, args);
        String outputName = agentInvoker.outputName();
        if (outputName != null && !outputName.isBlank()) {
            cognisphere.writeState(outputName, response);
        }
        cognisphere.registerAgentCall(agentInvoker, invokedAgent, args, response);
        return response;
    }
}
