package dev.langchain4j.agentic;

import java.lang.reflect.Method;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

public record AgentSpecification(String name, Method method, String description) {

    public static AgentSpecification fromMethod(Method method) {
        Agent annotation = method.getAnnotation(Agent.class);
        String name = isNullOrBlank(annotation.name()) ? method.getName() : annotation.name();
        String description = String.join("\n", annotation.value());
        return new AgentSpecification(name, method, description);
    }
}
