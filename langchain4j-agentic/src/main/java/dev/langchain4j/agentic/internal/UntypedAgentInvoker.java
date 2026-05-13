package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.reflect.Method;

public final class UntypedAgentInvoker extends AbstractAgentInvoker {

    public UntypedAgentInvoker(Method method, InternalAgent agent) {
        super(method, agent);
    }

    @Override
    public AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) {
        for (AgentArgument arg : arguments()) {
            if (agenticScope.readState(arg.name()) == null) {
                throw new MissingArgumentException(arg.name());
            }
        }
        return new AgentInvocationArguments(agenticScope.state(), new Object[]{agenticScope.state()});
    }

    @Override
    public String toString() {
        return "UntypedAgentInvoker[" +
                "method=" + method + ", " +
                "agent=" + agent + ']';
    }
}
