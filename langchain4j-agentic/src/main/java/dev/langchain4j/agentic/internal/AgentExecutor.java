package dev.langchain4j.agentic.internal;

import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.agentError;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.agentSkipped;
import static dev.langchain4j.agentic.scope.DefaultAgenticScope.isSerializable;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentCondition;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticSystemSuspendedException;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.TokenStream;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            AgentInvocationException e,
            DefaultAgenticScope agenticScope,
            Object invokedAgent,
            PlannerExecutor planner,
            AgentInvocationArguments args,
            boolean plannerAlreadyNotified) {
        ErrorRecoveryResult recoveryResult = agenticScope.handleError(agentInvoker.name(), e);
        return switch (recoveryResult.type()) {
            case THROW_EXCEPTION -> {
                ActivationConditions.clearActivation(this, agenticScope);
                throw e;
            }
            case RETRY -> internalExecute(agenticScope, invokedAgent, planner, false);
            case RETURN_RESULT ->
                plannerAlreadyNotified
                        ? recoveryResult.result()
                        : completeAgentInvocation(recoveryResult.result(), agenticScope, invokedAgent, planner, args);
        };
    }

    private Object internalExecute(
            DefaultAgenticScope agenticScope, Object invokedAgent, PlannerExecutor planner, boolean async) {
        AgentInvocationArguments args = null;
        try {
            AgentCondition condition = agentInvoker.activationCondition();
            if (condition != null && !ActivationConditions.isActivated(this, agenticScope)) {
                if (!isActive(condition, agenticScope)) {
                    return skip(agenticScope, planner, "activation condition is false: " + condition.description());
                }
                ActivationConditions.markActivated(this, agenticScope);
            }

            try {
                args = agentInvoker.toInvocationArguments(agenticScope);
            } catch (MissingArgumentException e) {
                if (optional()) {
                    return skip(agenticScope, planner, "missing argument '" + e.argumentName() + "'");
                }
                throw e;
            }

            Object response = agentResponse(agenticScope, invokedAgent, planner, args, async);
            return completeAgentInvocation(response, agenticScope, invokedAgent, planner, args);
        } catch (AgenticSystemSuspendedException e) {
            if (planner != null) {
                planner.onSubagentSuspended();
            }
            return null;
        } catch (AgentInvocationException e) {
            return handleAgentFailure(e, agenticScope, invokedAgent, planner, args, false);
        }
    }

    private boolean isActive(AgentCondition condition, DefaultAgenticScope agenticScope) {
        try {
            return condition.test(agenticScope);
        } catch (RuntimeException e) {
            AgentInvocationException failure =
                    new AgentInvocationException("Activation condition of agent '" + name() + "' failed", e);
            agentError(agentInvoker.listener(), agenticScope, agentInvoker, Map.of(), failure);
            throw failure;
        }
    }

    /**
     * Skips this agent without invoking it: nothing is written to the scope and the response is the current value
     * of the output key. The skip is a plain value also for an async agent, and its invocation is not registered in
     * the scope, but the planner is notified so that it can move on to the next agent.
     */
    private Object skip(DefaultAgenticScope agenticScope, PlannerExecutor planner, String reason) {
        LOG.info("Skipping agent '{}': {}", name(), reason);
        String outputKey = outputKey();
        Object response = outputKey == null || outputKey.isBlank() ? null : agenticScope.readState(outputKey);
        agentSkipped(agentInvoker.listener(), agenticScope, agentInvoker, reason);
        if (planner != null) {
            planner.onSubagentInvoked(new AgentInvocation(type(), name(), agentId(), Map.of(), response, reason));
        }
        return response;
    }

    private Object completeAgentInvocation(
            Object response,
            DefaultAgenticScope agenticScope,
            Object invokedAgent,
            PlannerExecutor planner,
            AgentInvocationArguments args) {
        ActivationConditions.clearActivation(this, agenticScope);
        String outputKey = agentInvoker.outputKey();
        if (outputKey != null && !outputKey.isBlank()) {
            Object stateValue = response instanceof ResultWithAgenticScope<?> r ? r.result() : response;
            agenticScope.writeState(outputKey, stateValue);
        }

        Map<String, Object> namedArgs = args != null ? args.namedArgs() : Map.of();
        AgentInvocation agentInvocation = new AgentInvocation(
                type(), name(), agentId(), namedArgs, isSerializable(response) ? response : "<unknown>");
        agenticScope.registerAgentInvocation(agentInvocation, invokedAgent);
        if (planner != null) {
            planner.onSubagentInvoked(agentInvocation);
        }
        return response;
    }

    private Object agentResponse(
            DefaultAgenticScope agenticScope,
            Object invokedAgent,
            PlannerExecutor planner,
            AgentInvocationArguments args,
            boolean async) {
        if (async) {
            return new AsyncResponse<>(() -> {
                try {
                    return agentInvoker.invoke(agenticScope, invokedAgent, args);
                } catch (AgentInvocationException e) {
                    return handleAgentFailure(e, agenticScope, invokedAgent, planner, args, true);
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
    public Class<? extends Planner> plannerType() {
        return agentInvoker.plannerType();
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
    public boolean optional() {
        return agentInvoker.optional();
    }

    @Override
    public AgentCondition activationCondition() {
        return agentInvoker.activationCondition();
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
    public boolean compensateOnError() {
        return agentInvoker.compensateOnError();
    }

    @Override
    public void enableCrossAgentCompensation() {
        agentInvoker.enableCrossAgentCompensation();
    }

    @Override
    public void registerInheritedParentListener(AgentListener parentListener) {
        agentInvoker.registerInheritedParentListener(parentListener);
    }

    @Override
    public void appendId(final String idSuffix) {
        agentInvoker.appendId(idSuffix);
    }

    @Override
    public AgentListener listener() {
        return agentInvoker.listener();
    }

    @Override
    public <T extends AgentInstance> T as(Class<T> agentInstanceClass) {
        return agentInvoker.as(agentInstanceClass);
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
