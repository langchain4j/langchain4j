package dev.langchain4j.agent.tool;

import dev.langchain4j.internal.Json;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Map;

import static dev.langchain4j.agent.tool.ToolDoubleParameterHandlers.handleDouble;

public class ToolExecutor {

    private final Object object;
    private final Method method;
    private final String[] parameterNames;
    private final Class<?>[] parameterTypes;

    public ToolExecutor(Object object, Method method) {
        this.object = object;
        this.method = method;
        this.parameterTypes = method.getParameterTypes();
        this.parameterNames = Arrays.stream(method.getParameters())
                .map(Parameter::getName)
                .toArray(String[]::new);
    }

    @NotNull
    public ToolSpecification toolSpecification() {
        return ToolSpecifications.toolSpecificationFrom(method);
    }

    @NotNull
    public ToolSpecification toolSpecification(String description) {
        return ToolSpecifications.toolSpecificationFrom(method, description);
    }

    @NotNull
    public ToolSpecification toolSpecification(String description, ToolParameters toolParameters) {
        return ToolSpecifications.toolSpecificationFrom(method, description, toolParameters);
    }

    @NotNull
    public String[] parameterNames() {
        return parameterNames;
    }

    @NotNull
    public Class<?>[] parameterTypes() {
        return parameterTypes;
    }

    public Object execute(Object[] args) throws IllegalAccessException, InvocationTargetException {
        if (method.getReturnType() == void.class) return "Success"; // todo why return "Success"? try exposed as user-defined in context
        try {
            return method.invoke(object, args);
        } catch (IllegalAccessException e) {
            method.setAccessible(true);
            return method.invoke(object, args);
        }
    }

    protected final Object[] prepareArguments(Map<String, Object> argumentsMap) {
        @NotNull final String[] parameterNames = parameterNames();
        @NotNull final Class<?>[] parameterTypes = parameterTypes();
        @NotNull final Object[] arguments = new Object[parameterNames.length];

        for (int i = 0; i < arguments.length; i++) {
            final String parameterName = parameterNames[i];
            if (!argumentsMap.containsKey(parameterName)) continue;

            final Class<?> parameterType = parameterTypes[i];
            final Object argument = argumentsMap.get(parameterName);
            if (argument instanceof Double) {
                handleDouble(parameterType, ((Double) argument));
            }

            // todo instanceof CharSequence ?
            arguments[i] = argument;
        }

        return arguments;
    }

    public final String execute(ToolExecutionRequest toolExecutionRequest) {
        log.debug("About to execute {}", toolExecutionRequest);
        final Object[] arguments = prepareArguments(toolExecutionRequest.argumentsAsMap());

        try {
            final Object response = execute(arguments);
            final String result = Json.toJson(response);
            log.debug("Tool execution result: {}", result);
            return result;
        } catch (Exception e) {
            Throwable cause = e.getCause();
            log.error("Error while executing tool", cause);
            return cause.getMessage();
        }
    }

    protected static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);
}
