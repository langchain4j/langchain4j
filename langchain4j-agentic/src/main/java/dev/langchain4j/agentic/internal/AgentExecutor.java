package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.cognisphere.DefaultCognisphere;

public record AgentExecutor(AgentInvoker agentInvoker, Object agent) {

    public String agentName() {
        return agentInvoker.name();
    }

    public Object execute(DefaultCognisphere cognisphere) {
        Object invokedAgent = agent instanceof CognisphereOwner co ? co.withCognisphere(cognisphere) : agent;
        try {
            return internalExecute(cognisphere, invokedAgent);
        } catch (AgentInvocationException e) {
            ErrorRecoveryResult recoveryResult = cognisphere.handleError(agentInvoker.name(), e);
            return switch (recoveryResult.type()) {
                case THROW_EXCEPTION -> throw e;
                case RETRY -> internalExecute(cognisphere, invokedAgent);
                case RETURN_RESULT -> recoveryResult.result();
            };
        }
    }

    private Object internalExecute(DefaultCognisphere cognisphere, Object invokedAgent) {
        Object[] args = agentInvoker.toInvocationArguments(cognisphere);
        Object response = agentInvoker.invoke(invokedAgent, args);
        String outputName = agentInvoker.outputName();
        if (outputName != null && !outputName.isBlank()) {
            cognisphere.writeState(outputName, response);
        }
        cognisphere.registerAgentCall(agentInvoker.name(), invokedAgent, args, response);
        return response;
    }
}
