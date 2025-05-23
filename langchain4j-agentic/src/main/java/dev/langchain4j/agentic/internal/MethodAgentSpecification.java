package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.cognisphere.Cognisphere;
import java.lang.reflect.Method;
import java.util.List;

import static dev.langchain4j.agentic.internal.AgentUtil.methodInvocationArguments;

public record MethodAgentSpecification(Method method, String name, String description, String outputName, List<AgentUtil.AgentArgument> arguments) implements AgentSpecification {

    @Override
    public String toCard() {
        List<String> agentArguments = arguments.stream()
                .map(AgentUtil.AgentArgument::name)
                .filter(a -> !a.equals("@MemoryId"))
                .toList();
        return "{" + name + ": " + description + ", " + agentArguments + "}";
    }

    @Override
    public Object[] toInvocationArguments(Cognisphere cognisphere) {
        return methodInvocationArguments(cognisphere, arguments);
    }
}
