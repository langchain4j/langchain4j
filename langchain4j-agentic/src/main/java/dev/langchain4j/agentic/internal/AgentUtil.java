package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.Cognisphere;
import dev.langchain4j.service.MemoryId;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class AgentUtil {

    public static Object[] methodInvocationArguments(Cognisphere cognisphere, Method method) {
        Parameter[] parameters = method.getParameters();
        if (parameters.length == 1) {
            return singleParamArg(cognisphere, parameters[0]);
        }

        Object[] invocationArgs = new Object[parameters.length];
        int i = 0;
        for (Parameter parameter : parameters) {
            if (parameter.getAnnotation(MemoryId.class) != null) {
                invocationArgs[i++] = cognisphere.id();
                continue;
            }
            String argName = AgentSpecification.parameterName(parameter);
            Object argValue = cognisphere.readState(argName);
            if (argValue == null) {
                throw new IllegalArgumentException("Missing argument: " + argName);
            }
            invocationArgs[i++] = parseArgument(argValue, parameter.getType());
        }
        return invocationArgs;
    }

    static Object[] singleParamArg(Cognisphere cognisphere, Parameter parameter) {
        if (parameter.getAnnotation(MemoryId.class) != null) {
            return new Object[]{cognisphere.id()};
        }
        Object argValue = AgentSpecification.optionalParameterName(parameter)
                .map(cognisphere::readState)
                .orElseGet(() -> cognisphere.getState().values().iterator().next());
        return new Object[]{parseArgument(argValue, parameter.getType())};
    }

    static Object parseArgument(Object argValue, Class<?> type) {
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
