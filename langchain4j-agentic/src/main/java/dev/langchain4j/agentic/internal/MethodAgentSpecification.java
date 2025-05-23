package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.Cognisphere;
import dev.langchain4j.service.MemoryId;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;

import static dev.langchain4j.agentic.internal.AgentUtil.methodInvocationArguments;
import static dev.langchain4j.agentic.internal.AgentUtil.parseArgument;

public record MethodAgentSpecification(Method method, String name, String description, List<String> arguments) implements AgentSpecification {

    @Override
    public String toCard() {
        return "{" + name + ": " + description + ", " + arguments + "}";
    }

    @Override
    public Object[] toInvocationArguments(Cognisphere cognisphere) {
        return methodInvocationArguments(cognisphere, method);
    }
}
