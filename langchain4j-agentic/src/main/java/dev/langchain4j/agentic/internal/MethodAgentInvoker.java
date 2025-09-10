package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.reflect.Method;
import java.util.List;

import static dev.langchain4j.agentic.internal.AgentUtil.methodInvocationArguments;

public record MethodAgentInvoker(Method method, String name, String description, String outputName, boolean async, List<AgentUtil.AgentArgument> arguments) implements AgentInvoker {

    @Override
    public String toCard() {
        List<String> agentArguments = arguments.stream()
                .map(AgentUtil.AgentArgument::name)
                .filter(a -> !a.equals("@MemoryId"))
                .toList();
        return "{" + name + ": " + description + ", " + agentArguments + "}";
    }

    @Override
    public Object[] toInvocationArguments(AgenticScope agenticScope) throws MissingArgumentException {
        return methodInvocationArguments(agenticScope, arguments);
    }
}
