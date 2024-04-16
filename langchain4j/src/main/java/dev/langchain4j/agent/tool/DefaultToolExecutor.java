package dev.langchain4j.agent.tool;

import dev.langchain4j.internal.Json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Objects;

public class DefaultToolExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultToolExecutor.class);

    private final Object object;
    private final Method method;
    private final ToolJsonSchemas toolJsonSchemas;

    public DefaultToolExecutor(Object object, Method method, ToolJsonSchemas toolJsonSchemas) {
        this.object = Objects.requireNonNull(object, "object");
        this.method = Objects.requireNonNull(method, "method");
        this.toolJsonSchemas = Objects.requireNonNull(toolJsonSchemas, "toolJsonSchemas");
    }

    public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
        log.debug("About to execute {} for memoryId {}", toolExecutionRequest, memoryId);

        Map<String, Object> argumentsFromRequest;
        try {
            argumentsFromRequest =
                    toolJsonSchemas.deserialize(toolExecutionRequest.arguments(), method);
        } catch (Exception e) {
            log.error("Error while deserializing arguments", e);
            return e.getMessage();
        }
        Object[] arguments = prepareArguments(method, argumentsFromRequest, memoryId);

        for (int tried = 0; true; tried += 1) {
            String result;
            try {
                result = execute(arguments);

            } catch (IllegalAccessException e) {
                if (tried == 0) {
                    method.setAccessible(true);
                    continue;
                }
                throw new RuntimeException(e);

            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                log.error("Error while executing tool", cause);
                return cause.getMessage();
            }

            log.debug("Tool execution result: {}", result);
            return result;
        }
    }

    private String execute(Object[] arguments)
            throws IllegalAccessException, InvocationTargetException {
        Object result = method.invoke(object, arguments);
        Class<?> returnType = method.getReturnType();
        if (returnType == void.class) {
            return "Success";
        } else if (returnType == String.class) {
            return (String) result;
        } else {
            return Json.toJson(result);
        }
    }

    Object[] prepareArguments(Method method, Map<String, Object> argumentsMap, Object memoryId) {
        Parameter[] parameters = method.getParameters();
        Object[] arguments = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(ToolMemoryId.class)) {
                arguments[i] = memoryId;
                continue;
            }
            String parameterName = parameters[i].getName();
            if (argumentsMap.containsKey(parameterName)) {
                arguments[i] = argumentsMap.get(parameterName);
            }
        }
        return arguments;
    }
}
