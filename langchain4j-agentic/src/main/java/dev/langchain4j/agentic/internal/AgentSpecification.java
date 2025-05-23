package dev.langchain4j.agentic.internal;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.service.V;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;

import static dev.langchain4j.agentic.internal.AgentUtil.argumentsFromMethod;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

public interface AgentSpecification {

    String name();
    String description();
    String outputName();
    Method method();

    String toCard();

    Object[] toInvocationArguments(Cognisphere cognisphere);

    default Object invoke(Object agent, Object... args) {
        try {
            return method().invoke(agent, args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke agent method: " + method(), e);
        }
    }

    static AgentSpecification fromMethod(AgentInstance agent, Method method) {
        Agent annotation = method.getAnnotation(Agent.class);
        String name = isNullOrBlank(annotation.name()) ? method.getName() : annotation.name();
        return fromMethodAndSpec(method, name, annotation.value(), agent.outputName());
    }

    static AgentSpecification fromMethodAndSpec(Method method, String name, String description, String outputName) {
        if (method.getDeclaringClass() == UntypedAgent.class) {
            return new UntypedAgentSpecification(method, name, description, outputName);
        }

        return new MethodAgentSpecification(method, name, description, outputName, argumentsFromMethod(method));
    }

    static String parameterName(Parameter parameter) {
        return optionalParameterName(parameter)
                .orElseThrow(() -> new IllegalArgumentException("Parameter name not specified and no @P or @V annotation present: " + parameter));
    }

    static Optional<String> optionalParameterName(Parameter parameter) {
        P p = parameter.getAnnotation(P.class);
        if (p != null) {
            return Optional.of(p.value());
        }
        V v = parameter.getAnnotation(V.class);
        if (v != null) {
            return Optional.of(v.value());
        }
        return parameter.isNamePresent() ? Optional.of(parameter.getName()) : java.util.Optional.empty();
    }
}
