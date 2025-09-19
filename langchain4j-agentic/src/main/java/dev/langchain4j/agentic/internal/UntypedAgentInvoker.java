package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.reflect.Method;

public record UntypedAgentInvoker(Method method, AgentSpecification agentSpecification) implements AgentInvoker {

    @Override
    public String name() {
        return agentSpecification.name();
    }

    @Override
    public String uniqueName() {
        return agentSpecification.uniqueName();
    }

    @Override
    public String description() {
        return agentSpecification.description();
    }

    @Override
    public String outputName() {
        return agentSpecification.outputName();
    }

    @Override
    public boolean async() {
        return agentSpecification.async();
    }

    @Override
    public String toCard() {
        return "{" + uniqueName() + ": " + description() + "}";
    }

    @Override
    public Object[] toInvocationArguments(AgenticScope agenticScope) {
        return new Object[] { agenticScope.state() };
    }
}
