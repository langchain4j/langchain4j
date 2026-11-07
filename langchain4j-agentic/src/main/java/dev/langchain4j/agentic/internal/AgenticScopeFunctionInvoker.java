package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.reflect.Method;

/**
 * Invokes an {@link dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction}. The function receives the whole
 * {@link AgenticScope}, but its declared inputs are still resolved from it beforehand: this validates that they are
 * present, converts them to their declared types writing them back to the scope, and reports them to listeners.
 */
final class AgenticScopeFunctionInvoker extends AbstractAgentInvoker {

    AgenticScopeFunctionInvoker(Method method, InternalAgent agent) {
        super(method, agent);
    }

    @Override
    public AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) throws MissingArgumentException {
        AgentInvocationArguments inputs = AgentUtil.agentInvocationArguments(agenticScope, arguments());
        return new AgentInvocationArguments(inputs.namedArgs(), new Object[] {agenticScope});
    }
}
