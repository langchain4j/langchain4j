package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;

public record AgentExecutor(AgentInvoker agentInvoker, Object agent) {

    public String agentName() {
        return agentInvoker.name();
    }

    public Object execute(DefaultAgenticScope agenticScope) {
        Object invokedAgent = agent instanceof AgenticScopeOwner co ? co.withAgenticScope(agenticScope) : agent;
        try {
            return internalExecute(agenticScope, invokedAgent);
        } catch (AgentInvocationException e) {
            ErrorRecoveryResult recoveryResult = agenticScope.handleError(agentInvoker.name(), e);
            return switch (recoveryResult.type()) {
                case THROW_EXCEPTION -> throw e;
                case RETRY -> internalExecute(agenticScope, invokedAgent);
                case RETURN_RESULT -> recoveryResult.result();
            };
        }
    }

    private Object internalExecute(DefaultAgenticScope agenticScope, Object invokedAgent) {
        Object[] args = agentInvoker.toInvocationArguments(agenticScope);
        Object response = agentInvoker.invoke(invokedAgent, args);
        String outputName = agentInvoker.outputName();
        if (outputName != null && !outputName.isBlank()) {
            agenticScope.writeState(outputName, response);
        }
        agenticScope.registerAgentCall(agentInvoker.name(), invokedAgent, args, response);
        return response;
    }
}
