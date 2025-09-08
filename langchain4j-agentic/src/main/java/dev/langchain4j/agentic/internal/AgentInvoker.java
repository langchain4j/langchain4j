package dev.langchain4j.agentic.internal;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.service.V;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;

import static dev.langchain4j.agentic.internal.AgentUtil.argumentsFromMethod;

public interface AgentInvoker {

    String name();
    String description();
    String outputName();
    boolean async();
    Method method();

    String toCard();

    Object[] toInvocationArguments(AgenticScope agenticScope) throws MissingArgumentException;

    default Object invoke(Object agent, Object... args) throws AgentInvocationException {
        try {
            return method().invoke(agent, args);
        } catch (Exception e) {
            throw new AgentInvocationException("Failed to invoke agent method: " + method(), e);
        }
    }

    static AgentInvoker fromMethod(AgentSpecification agent, Method method) {
        return fromMethodAndSpec(method, agent.name(), agent.description(), agent.outputName(), agent.async());
    }

    static AgentInvoker fromMethodAndSpec(Method method, String name, String description, String outputName, boolean async) {
        if (method.getDeclaringClass() == UntypedAgent.class) {
            return new UntypedAgentInvoker(method, name, description, outputName, async);
        }

        return new MethodAgentInvoker(method, name, description, outputName, async, argumentsFromMethod(method));
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
