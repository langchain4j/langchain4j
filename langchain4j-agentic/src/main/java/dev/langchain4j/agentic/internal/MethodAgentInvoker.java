package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.reflect.Method;

public final class MethodAgentInvoker extends AbstractAgentInvoker {

    public MethodAgentInvoker(Method method, InternalAgent agent) {
        super(method, agent);
    }

    @Override
    public AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) throws MissingArgumentException {
        return AgentUtil.agentInvocationArguments(agenticScope, arguments());
    }

    @Override
    public String toString() {
        return "MethodAgentInvoker[" +
                "method=" + method + ", " +
                "agent=" + agent + ']';
    }
}
