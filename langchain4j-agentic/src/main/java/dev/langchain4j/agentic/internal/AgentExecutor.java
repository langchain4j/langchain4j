package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record AgentExecutor(AgentInvoker agentInvoker, Object agent) {

    private static final Logger LOG = LoggerFactory.getLogger(AgentExecutor.class);

    public String agentName() {
        return agentInvoker.name();
    }

    public Object execute(DefaultAgenticScope agenticScope) {
        return execute(agenticScope, agentInvoker.async());
    }

    public Object syncExecute(DefaultAgenticScope agenticScope) {
        if (agentInvoker.async()) {
            LOG.info("Executing '{}' agent in a sync way even if declared as async", agentInvoker.name());
        }
        return execute(agenticScope, false);
    }

    private Object execute(DefaultAgenticScope agenticScope, boolean async) {
        Object invokedAgent = agent instanceof AgenticScopeOwner co ? co.withAgenticScope(agenticScope) : agent;
        return internalExecute(agenticScope, invokedAgent, async);
    }

    private Object handleAgentFailure(AgentInvocationException e, DefaultAgenticScope agenticScope, Object invokedAgent) {
        ErrorRecoveryResult recoveryResult = agenticScope.handleError(agentInvoker.name(), e);
        return switch (recoveryResult.type()) {
            case THROW_EXCEPTION -> throw e;
            case RETRY -> internalExecute(agenticScope, invokedAgent, false);
            case RETURN_RESULT -> recoveryResult.result();
        };
    }

    private Object internalExecute(DefaultAgenticScope agenticScope, Object invokedAgent, boolean async) {
        try {
            Object[] args = agentInvoker.toInvocationArguments(agenticScope);
            Object response = async ?
                    new AsyncResponse<>(() -> {
                        try {
                            return agentInvoker.invoke(invokedAgent, args);
                        } catch (AgentInvocationException e) {
                            return handleAgentFailure(e, agenticScope, invokedAgent);
                        }
                    }) :
                    agentInvoker.invoke(invokedAgent, args);
            String outputName = agentInvoker.outputName();
            if (outputName != null && !outputName.isBlank()) {
                agenticScope.writeState(outputName, response);
            }
            agenticScope.registerAgentCall(agentInvoker.name(), invokedAgent, args, response);
            return response;
        } catch (AgentInvocationException e) {
            return handleAgentFailure(e, agenticScope, invokedAgent);
        }
    }
}
