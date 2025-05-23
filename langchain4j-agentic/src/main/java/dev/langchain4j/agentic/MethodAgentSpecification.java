package dev.langchain4j.agentic;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.service.V;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

public record MethodAgentSpecification(Method method, String name, String description, List<String> arguments) implements AgentSpecification {

    @Override
    public boolean isWorkflowAgent() {
        return false;
    }

    @Override
    public String toCard() {
        return "{" + name + ": " + description + ", " + arguments + "}";
    }

    @Override
    public Object[] toInvocationArguments(Map<String, ?> arguments) {
        Parameter[] parameters = method.getParameters();
        if (parameters.length == 1) {
            Object argValue = AgentSpecification.optionalParameterName(parameters[0])
                    .map(argName -> (Object) arguments.get(argName))
                    .orElseGet(() -> {
                        if (arguments.size() != 1) {
                            throw new IllegalArgumentException("Expected exactly one argument for method: " + method.getName());
                        }
                        return arguments.values().iterator().next();
                    });
            return new Object[] { parseArgument(argValue, parameters[0].getType()) };
        }

        Object[] invocationArgs = new Object[parameters.length];
        int i = 0;
        for (Parameter parameter : parameters) {
            String argName = AgentSpecification.parameterName(parameter);
            Object argValue = arguments.get(argName);
            if (argValue == null) {
                throw new IllegalArgumentException("Missing argument: " + argName);
            }
            invocationArgs[i++] = parseArgument(argValue, parameter.getType());
        }
        return invocationArgs;
    }

    private static Object parseArgument(Object argValue, Class<?> type) {
        if (argValue instanceof String s) {
            return switch (type.getName()) {
                case "java.lang.String" -> s;
                case "int", "java.lang.Integer" -> Integer.parseInt(s);
                case "long", "java.lang.Long" -> Long.parseLong(s);
                case "double", "java.lang.Double" -> Double.parseDouble(s);
                case "boolean", "java.lang.Boolean" -> Boolean.parseBoolean(s);
                default -> throw new IllegalArgumentException("Unsupported type: " + type);
            };
        }
        return argValue;
    }
}
