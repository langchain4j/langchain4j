package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgentExecution;
import dev.langchain4j.agentic.scope.AgentExecutionListener;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record AgentExecutor(AgentInvoker agentInvoker, Object agent) implements AgentInstance {

    private static final Logger LOG = LoggerFactory.getLogger(AgentExecutor.class);

    public Object execute(DefaultAgenticScope agenticScope, AgentExecutionListener listener) {
        return execute(agenticScope, listener, agentInvoker.async());
    }

    public Object syncExecute(DefaultAgenticScope agenticScope, AgentExecutionListener listener) {
        if (agentInvoker.async()) {
            LOG.info("Executing '{}' agent in a sync way even if declared as async", agentInvoker.name());
        }
        return execute(agenticScope, listener, false);
    }

    private Object execute(DefaultAgenticScope agenticScope, AgentExecutionListener listener, boolean async) {
        Object invokedAgent = (agent instanceof AgenticScopeOwner co ? co.withAgenticScope(agenticScope) : agent);
        return internalExecute(agenticScope, invokedAgent, listener, async);
    }

    private Object handleAgentFailure(
            AgentInvocationException e, DefaultAgenticScope agenticScope, Object invokedAgent, AgentExecutionListener listener) {
        ErrorRecoveryResult recoveryResult = agenticScope.handleError(agentInvoker.name(), e);
        return switch (recoveryResult.type()) {
            case THROW_EXCEPTION -> throw e;
            case RETRY -> internalExecute(agenticScope, invokedAgent, listener, false);
            case RETURN_RESULT -> recoveryResult.result();
        };
    }

    private Object internalExecute(DefaultAgenticScope agenticScope, Object invokedAgent, AgentExecutionListener listener, boolean async) {
        try {
            AgentInvocationArguments args = agentInvoker.toInvocationArguments(agenticScope);
            Object response = async
                    ? new AsyncResponse<>(() -> {
                        try {
                            return agentInvoker.invoke(agenticScope, invokedAgent, args);
                        } catch (AgentInvocationException e) {
                            return handleAgentFailure(e, agenticScope, invokedAgent, listener);
                        }
                    })
                    : agentInvoker.invoke(agenticScope, invokedAgent, args);
            String outputKey = agentInvoker.outputKey();
            if (outputKey != null && !outputKey.isBlank()) {
                agenticScope.writeState(outputKey, response);
            }
            agenticScope.registerAgentCall(agentInvoker, invokedAgent, args, response);
            if (listener != null) {
                listener.onAgentExecuted(new AgentExecution(agentInvoker, agent, args.namedArgs(), response));
            }
            return response;
        } catch (AgentInvocationException e) {
            return handleAgentFailure(e, agenticScope, invokedAgent, listener);
        }
    }

    @Override
    public String uniqueName() {
        return agentInvoker.uniqueName();
    }

    @Override
    public String outputKey() {
        return agentInvoker.outputKey();
    }

    @Override
    public String[] argumentNames() {
        return agentInvoker.argumentNames();
    }

    @Override
    public String toCard() {
        return agentInvoker.toCard();
    }
}
