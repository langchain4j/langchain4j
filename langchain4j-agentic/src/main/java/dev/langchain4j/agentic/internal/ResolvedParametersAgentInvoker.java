package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.scope.AgenticScope;

/**
 * Invokes a non-AI agent method having parameters provided by a
 * {@link dev.langchain4j.agentic.declarative.SupplierParameterResolver}: its agent arguments are read from the
 * {@link AgenticScope} as usual, and the resolved values are added to the positional arguments only.
 */
final class ResolvedParametersAgentInvoker extends AbstractAgentInvoker {

    private final ResolvedParameters parameters;

    ResolvedParametersAgentInvoker(InternalAgent agent, ResolvedParameters parameters) {
        super(parameters.method(), agent);
        this.parameters = parameters;
    }

    @Override
    public AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) throws MissingArgumentException {
        AgentInvocationArguments agentArguments = AgentUtil.agentInvocationArguments(agenticScope, arguments());
        Object[] positionalArgs;
        try {
            positionalArgs = parameters.arguments(agentArguments.positionalArgs());
        } catch (RuntimeException e) {
            throw new AgentInvocationException("Failed to resolve the parameters of agent '" + name() + "'", e);
        }
        return new AgentInvocationArguments(agentArguments.namedArgs(), positionalArgs);
    }

    @Override
    public String toString() {
        return "ResolvedParametersAgentInvoker[" + "method=" + method + ", " + "agent=" + agent + ']';
    }
}
