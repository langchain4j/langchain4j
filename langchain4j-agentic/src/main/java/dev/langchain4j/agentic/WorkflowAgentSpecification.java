package dev.langchain4j.agentic;

import java.lang.reflect.Method;
import java.util.Map;

public record WorkflowAgentSpecification(Method method, String name) implements AgentSpecification {

    @Override
    public boolean isWorkflowAgent() {
        return true;
    }

    @Override
    public String toCard() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object[] toInvocationArguments(Map<String, ?> arguments) {
        return new Object[] { arguments };
    }
}
