package dev.langchain4j.agent.tool;

import dev.langchain4j.internal.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final Object object;
    private final Method method;

    public ToolExecutor(Object object, Method method) {
        this.object = object;
        this.method = method;
    }

    public String execute(Map<String, Object> argumentsMap) {

        Object[] arguments = prepareArguments(argumentsMap);

        try {
            return execute(arguments);
        } catch (IllegalAccessException e) {
            try {
                method.setAccessible(true);
                return execute(arguments);
            } catch (IllegalAccessException e2) {
                throw new RuntimeException(e2);
            } catch (InvocationTargetException e2) {
                Throwable cause = e2.getCause();
                log.error("Error while executing tool", cause);
                return cause.getMessage();
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            log.error("Error while executing tool", cause);
            return cause.getMessage();
        }
    }

    private String execute(Object[] arguments) throws IllegalAccessException, InvocationTargetException {
        Object result = method.invoke(object, arguments);
        if (method.getReturnType() == void.class) {
            return "Success";
        }
        return Json.toJson(result);
    }

    private Object[] prepareArguments(Map<String, Object> argumentsMap) {
        Parameter[] parameters = method.getParameters();
        Object[] arguments = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            String parameterName = parameters[i].getName();
            if (argumentsMap.containsKey(parameterName)) {
                arguments[i] = argumentsMap.get(parameterName);
            }
        }

        return arguments;
    }
}
