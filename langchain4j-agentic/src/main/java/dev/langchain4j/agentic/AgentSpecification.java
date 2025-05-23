package dev.langchain4j.agentic;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.service.V;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static dev.langchain4j.agentic.workflow.WorkflowAgent.WORKFLOW_AGENT_SUFFIX;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

public interface AgentSpecification {

    boolean isWorkflowAgent();

    String name();
    Method method();

    String toCard();

    Object[] toInvocationArguments(Map<String, ?> arguments);

    static AgentSpecification fromMethod(Method method) {
        Agent annotation = method.getAnnotation(Agent.class);
        String name = annotation == null || isNullOrBlank(annotation.name()) ? method.getName() : annotation.name();

        if (name.endsWith(WORKFLOW_AGENT_SUFFIX)) {
            return new WorkflowAgentSpecification(method, name);
        }

        String description = annotation == null ? "" : String.join("\n", annotation.value());
        List<String> arguments = method.getParameters().length == 1 ?
                List.of(optionalParameterName(method.getParameters()[0]).orElse("request")) :
                Stream.of(method.getParameters())
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
        return parameter.isNamePresent() ? Optional.of(parameter.getName()) : Optional.empty();
    }
}
