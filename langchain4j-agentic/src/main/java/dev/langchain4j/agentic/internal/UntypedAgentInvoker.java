package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.reflect.Method;

public final class UntypedAgentInvoker extends AbstractAgentInvoker {

    public UntypedAgentInvoker(Method method, InternalAgent agent) {
        super(method, agent);
    }

    @Override
    public AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) {
        return new AgentInvocationArguments(agenticScope.state(), new Object[]{agenticScope.state()});
    }

    @Override
    public String toString() {
        return "UntypedAgentInvoker[" +
                "method=" + method + ", " +
                "agent=" + agent + ']';
    }
}
