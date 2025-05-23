package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.Cognisphere;
import java.lang.reflect.Method;
import java.util.Map;

public record UntypedAgentSpecification(Method method, String name, String description) implements AgentSpecification {

    @Override
    public String toCard() {
        return "{" + name + ": " + description + "}";
    }

    @Override
    public Object[] toInvocationArguments(Cognisphere cognisphere) {
        return new Object[] { cognisphere.getState() };
    }
}
