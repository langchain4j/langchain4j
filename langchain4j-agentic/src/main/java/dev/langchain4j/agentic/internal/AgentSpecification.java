package dev.langchain4j.agentic.internal;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.Cognisphere;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

public interface AgentSpecification {

    String name();
    String description();
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

    static AgentSpecification fromMethod(Method method) {
        Agent annotation = method.getAnnotation(Agent.class);
        String name = annotation == null || isNullOrBlank(annotation.name()) ? method.getName() : annotation.name();
        String description = annotation == null ? "" : String.join("\n", annotation.value());

        if (method.getDeclaringClass() == UntypedAgent.class) {
            return new UntypedAgentSpecification(method, name, description);
        }

        List<String> arguments = method.getParameters().length == 1 ?
                List.of(optionalParameterName(method.getParameters()[0]).orElse("request")) :
                Stream.of(method.getParameters())
                        .filter(p -> p.getAnnotation(MemoryId.class) == null)
                        .map(AgentSpecification::parameterName)
                        .toList();

        return new MethodAgentSpecification(method, name, description, arguments);
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
