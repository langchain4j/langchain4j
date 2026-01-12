package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.service.TokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Type;
import java.util.List;

public record AgentExecutor(AgentInvoker agentInvoker, Object agent) implements AgentInstance, InternalAgent {

    private static final Logger LOG = LoggerFactory.getLogger(AgentExecutor.class);

    public Object execute(DefaultAgenticScope agenticScope, PlannerExecutor planner) {
        return execute(agenticScope, planner, agentInvoker.async());
    }

    public Object syncExecute(DefaultAgenticScope agenticScope, PlannerExecutor planner) {
        if (agentInvoker.async()) {
            LOG.info("Executing '{}' agent in a sync way even if declared as async", agentInvoker.name());
        }
        return execute(agenticScope, planner, false);
    }

    private Object execute(DefaultAgenticScope agenticScope, PlannerExecutor planner, boolean async) {
        Object invokedAgent = (agent instanceof AgenticScopeOwner co ? co.withAgenticScope(agenticScope) : agent);
        return internalExecute(agenticScope, invokedAgent, planner, async);
    }

    private Object handleAgentFailure(
            AgentInvocationException e, DefaultAgenticScope agenticScope, Object invokedAgent, PlannerExecutor planner) {
        ErrorRecoveryResult recoveryResult = agenticScope.handleError(agentInvoker.name(), e);
        return switch (recoveryResult.type()) {
            case THROW_EXCEPTION -> throw e;
            case RETRY -> internalExecute(agenticScope, invokedAgent, planner, false);
            case RETURN_RESULT -> recoveryResult.result();
        };
    }

    private Object internalExecute(DefaultAgenticScope agenticScope, Object invokedAgent, PlannerExecutor planner, boolean async) {
        try {
            AgentInvocationArguments args = agentInvoker.toInvocationArguments(agenticScope);
            Object response = agentResponse(agenticScope, invokedAgent, planner, args, async);
            String outputKey = agentInvoker.outputKey();
            if (outputKey != null && !outputKey.isBlank()) {
                agenticScope.writeState(outputKey, response);
            }
            AgentInvocation agentInvocation = new AgentInvocation(type(), name(), agentId(), args.namedArgs(), response);
            agenticScope.registerAgentInvocation(agentInvocation, invokedAgent);
            if (planner != null) {
                planner.onSubagentInvoked(agentInvocation);
            }
            return response;
        } catch (AgentInvocationException e) {
            return handleAgentFailure(e, agenticScope, invokedAgent, planner);
        }
    }

    private Object agentResponse(DefaultAgenticScope agenticScope, Object invokedAgent, PlannerExecutor planner, AgentInvocationArguments args, boolean async) {
        if (async) {
            return new AsyncResponse<>(() -> {
                try {
                    return agentInvoker.invoke(agenticScope, invokedAgent, args);
                } catch (AgentInvocationException e) {
                    return handleAgentFailure(e, agenticScope, invokedAgent, planner);
                }
            });
        }

        Object response = agentInvoker.invoke(agenticScope, invokedAgent, args);
        if (planner != null && response instanceof TokenStream tokenStream) {
            return planner.propagateStreaming() ? tokenStream : new StreamingResponse(tokenStream);
        }
        return response;
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

    @Override
    public AgenticSystemTopology topology() {
        return agentInvoker.topology();
    }

    @Override
    public AgentInstance parent() {
        return agentInvoker.parent();
    }

    @Override
    public void setParent(InternalAgent parent) {
        agentInvoker.setParent(parent);
    }

    @Override
    public void appendId(final String idSuffix) {
        agentInvoker.appendId(idSuffix);
    }

    @Override
    public AgentListener listener() {
        return agentInvoker.listener();
    }

    void setParent(InternalAgent parent, int index) {
        setParent(parent);
        propagateParentIndex(agentInvoker, index);
    }

    private void propagateParentIndex(InternalAgent agent, int index) {
        agent.appendId("$" + index);
        for (AgentInstance subagent : agent.subagents()) {
            if (subagent instanceof InternalAgent internalAgent) {
                propagateParentIndex(internalAgent, index);
            }
        }
    }
}
