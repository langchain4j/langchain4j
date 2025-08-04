package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.cognisphere.Cognisphere;
import java.lang.reflect.Method;

public record UntypedAgentInvoker(Method method, String name, String description, String outputName) implements AgentInvoker {

    @Override
    public String toCard() {
        return "{" + name + ": " + description + "}";
    }

    @Override
    public Object[] toInvocationArguments(Cognisphere cognisphere) {
        return new Object[] { cognisphere.state() };
    }
}
