package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgentInvocationListener;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Type;
import java.util.List;

public record AgentExecutor(AgentInvoker agentInvoker, Object agent) implements AgentInstance {

    private static final Logger LOG = LoggerFactory.getLogger(AgentExecutor.class);

    public Object execute(DefaultAgenticScope agenticScope, AgentInvocationListener listener) {
        return execute(agenticScope, listener, agentInvoker.async());
    }

    public Object syncExecute(DefaultAgenticScope agenticScope, AgentInvocationListener listener) {
        if (agentInvoker.async()) {
            LOG.info("Executing '{}' agent in a sync way even if declared as async", agentInvoker.name());
        }
        return execute(agenticScope, listener, false);
    }

    private Object execute(DefaultAgenticScope agenticScope, AgentInvocationListener listener, boolean async) {
        Object invokedAgent = (agent instanceof AgenticScopeOwner co ? co.withAgenticScope(agenticScope) : agent);
        return internalExecute(agenticScope, invokedAgent, listener, async);
    }

    private Object handleAgentFailure(
            AgentInvocationException e, DefaultAgenticScope agenticScope, Object invokedAgent, AgentInvocationListener listener) {
        ErrorRecoveryResult recoveryResult = agenticScope.handleError(agentInvoker.name(), e);
        return switch (recoveryResult.type()) {
            case THROW_EXCEPTION -> throw e;
            case RETRY -> internalExecute(agenticScope, invokedAgent, listener, false);
            case RETURN_RESULT -> recoveryResult.result();
        };
    }

    private Object internalExecute(DefaultAgenticScope agenticScope, Object invokedAgent, AgentInvocationListener listener, boolean async) {
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
            AgentInvocation agentInvocation = new AgentInvocation(type(), name(), agentId(), args.namedArgs(), response);
            agenticScope.registerAgentInvocation(agentInvocation, invokedAgent);
            if (listener != null) {
                listener.onAgentInvoked(agentInvocation);
            }
            return response;
        } catch (AgentInvocationException e) {
            return handleAgentFailure(e, agenticScope, invokedAgent, listener);
        }
    }
    @Override
    public Class<?> type() {
        return agentInvoker.type();
    }

    @Override
    public String name() {
        return agentInvoker.name();
    }

    @Override
    public String agentId() {
        return agentInvoker.agentId();
    }

    @Override
    public String description() {
        return agentInvoker.description();
    }

    @Override
    public Type outputType() {
        return agentInvoker.outputType();
    }

    @Override
    public String outputKey() {
        return agentInvoker.outputKey();
    }

    @Override
    public List<AgentArgument> arguments() {
        return agentInvoker.arguments();
    }

    @Override
    public List<AgentInstance> subagents() {
        return agentInvoker.subagents();
    }

    @Override
    public boolean async() {
        return agentInvoker.async();
    }
}
