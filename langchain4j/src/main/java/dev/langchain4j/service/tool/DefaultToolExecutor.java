package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolMemoryId;
import dev.langchain4j.internal.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.service.tool.ToolExecutionRequestUtil.argumentsAsMap;

public class DefaultToolExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultToolExecutor.class);

    private final Object object;
    private final Method method;

    public DefaultToolExecutor(Object object, Method method) {
        this.object = Objects.requireNonNull(object, "object");
        this.method = Objects.requireNonNull(method, "method");
    }

    public DefaultToolExecutor(Object object, ToolExecutionRequest toolExecutionRequest) {
        this.object = Objects.requireNonNull(object, "object");
        Objects.requireNonNull(toolExecutionRequest, "toolExecutionRequest");
        this.method = findMethod(object, toolExecutionRequest);
    }

    @Override
    public boolean isDirectReturn() {
        Tool toolAnnotation = method.getAnnotation(Tool.class);
        return toolAnnotation != null && toolAnnotation.directReturn();
    }

    Method findMethod(Object object, ToolExecutionRequest toolExecutionRequest) {
        String requestedMethodName = toolExecutionRequest.name();

        for (Method method : object.getClass().getDeclaredMethods()) {
            if (method.getName().equals(requestedMethodName)) {
                return method;
            }
        }

        throw new IllegalArgumentException(
                String.format("Method '%s' is not found in object '%s'",
                        requestedMethodName, object.getClass().getName()));
    }

    public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
        log.debug("About to execute {} for memoryId {}", toolExecutionRequest, memoryId);

        // TODO ensure this method never throws exceptions

        Map<String, Object> argumentsMap = argumentsAsMap(toolExecutionRequest.arguments());
        Object[] arguments = prepareArguments(method, argumentsMap, memoryId);
        try {
            String result = execute(arguments);
            log.debug("Tool execution result: {}", result);
            return result;
        } catch (IllegalAccessException e) {
            try {
                method.setAccessible(true);
                String result = execute(arguments);
                log.debug("Tool execution result: {}", result);
                return result;
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

    static Object[] prepareArguments(
            Method method,
            Map<String, Object> argumentsMap,
            Object memoryId
    ) {
        Parameter[] parameters = method.getParameters();
        Object[] arguments = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {

            Parameter parameter = parameters[i];

            if (parameter.isAnnotationPresent(ToolMemoryId.class)) {
                arguments[i] = memoryId;
                continue;
            }

            String parameterName = parameter.getName();
            if (argumentsMap.containsKey(parameterName)) {
                Object argument = argumentsMap.get(parameterName);
                Class<?> parameterClass = parameter.getType();
                Type parameterType = parameter.getParameterizedType();

                arguments[i] = coerceArgument(argument, parameterName, parameterClass, parameterType);
            }
        }

        return arguments;
    }

    static Object coerceArgument(
            Object argument,
            String parameterName,
            Class<?> parameterClass,
            Type parameterType) {
        if (parameterClass == String.class) {
            return argument.toString();
        }

        // TODO handle enum and collection of enums (e.g. wrong case, etc)
        if (parameterClass.isEnum()) {
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Class<Enum> enumClass = (Class<Enum>) parameterClass;
                try {
                    return Enum.valueOf(enumClass, Objects.requireNonNull(argument).toString());
                } catch (IllegalArgumentException e) {
                    // try to convert to uppercase as a last resort
                    return Enum.valueOf(enumClass, Objects.requireNonNull(argument).toString().toUpperCase());
                }
            } catch (Exception | Error e) {
                throw new IllegalArgumentException(String.format(
                        "Argument \"%s\" is not a valid enum value for %s: <%s>",
                        parameterName, parameterClass.getName(), argument), e);
            }
        }

        if (parameterClass == Boolean.class || parameterClass == boolean.class) {
            if (argument instanceof Boolean) {
                return argument;
            }
            throw new IllegalArgumentException(String.format(
                    "Argument \"%s\" is not convertable to %s, got %s: <%s>",
                    parameterName, parameterClass.getName(), argument.getClass().getName(), argument));
        }

        if (parameterClass == Double.class || parameterClass == double.class) {
            return getDoubleValue(argument, parameterName, parameterClass);
        }

        if (parameterClass == Float.class || parameterClass == float.class) {
            double doubleValue = getDoubleValue(argument, parameterName, parameterClass);
            checkBounds(doubleValue, parameterName, parameterClass, -Float.MIN_VALUE, Float.MAX_VALUE);
            return (float) doubleValue;
        }

        if (parameterClass == BigDecimal.class) {
            return BigDecimal.valueOf(getDoubleValue(argument, parameterName, parameterClass));
        }

        if (parameterClass == Integer.class || parameterClass == int.class) {
            return (int) getBoundedLongValue(
                    argument, parameterName, parameterClass, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }

        if (parameterClass == Long.class || parameterClass == long.class) {
            return getBoundedLongValue(
                    argument, parameterName, parameterClass, Long.MIN_VALUE, Long.MAX_VALUE);
        }

        if (parameterClass == Short.class || parameterClass == short.class) {
            return (short) getBoundedLongValue(
                    argument, parameterName, parameterClass, Short.MIN_VALUE, Short.MAX_VALUE);
        }

        if (parameterClass == Byte.class || parameterClass == byte.class) {
            return (byte) getBoundedLongValue(
                    argument, parameterName, parameterClass, Byte.MIN_VALUE, Byte.MAX_VALUE);
        }

        if (parameterClass == BigInteger.class) {
            return BigDecimal.valueOf(
                    getNonFractionalDoubleValue(argument, parameterName, parameterClass)).toBigInteger();
        }

        if (parameterClass.isArray() && argument instanceof Collection) {
            Class<?> type = parameterClass.getComponentType();
            if (type == String.class) {
                return ((Collection<String>) argument).toArray(new String[0]);
            }
            // TODO: Consider full type coverage.
        }

        if (Collection.class.isAssignableFrom(parameterClass) || Map.class.isAssignableFrom(parameterClass)) {
            return Json.fromJson(Json.toJson(argument), parameterType);
        }

        if (argument instanceof String) {
            return Json.fromJson(argument.toString(), parameterClass);
        } else {
            String result = Json.toJson(argument);
            return Json.fromJson(result, parameterClass);
        }
    }

    private static double getDoubleValue(
            Object argument,
            String parameterName,
            Class<?> parameterType
    ) {
        if (argument instanceof String) {
            try {
                return Double.parseDouble(argument.toString());
            } catch (Exception e) {
                // nothing, will be handled with bellow code
            }
        }
        if (!(argument instanceof Number)) {
            throw new IllegalArgumentException(String.format(
                    "Argument \"%s\" is not convertable to %s, got %s: <%s>",
                    parameterName, parameterType.getName(), argument.getClass().getName(), argument));
        }
        return ((Number) argument).doubleValue();
    }

    private static double getNonFractionalDoubleValue(
            Object argument,
            String parameterName,
            Class<?> parameterType
    ) {
        double doubleValue = getDoubleValue(argument, parameterName, parameterType);
        if (!hasNoFractionalPart(doubleValue)) {
            throw new IllegalArgumentException(String.format(
                    "Argument \"%s\" has non-integer value for %s: <%s>",
                    parameterName, parameterType.getName(), argument));
        }
        return doubleValue;
    }

    private static void checkBounds(
            double doubleValue,
            String parameterName,
            Class<?> parameterType,
            double minValue,
            double maxValue
    ) {
        if (doubleValue < minValue || doubleValue > maxValue) {
            throw new IllegalArgumentException(String.format(
                    "Argument \"%s\" is out of range for %s: <%s>",
                    parameterName, parameterType.getName(), doubleValue));
        }
    }

    private static long getBoundedLongValue(
            Object argument,
            String parameterName,
            Class<?> parameterType,
            long minValue,
            long maxValue
    ) {
        double doubleValue = getNonFractionalDoubleValue(argument, parameterName, parameterType);
        checkBounds(doubleValue, parameterName, parameterType, minValue, maxValue);
        return (long) doubleValue;
    }


    static boolean hasNoFractionalPart(Double doubleValue) {
        return doubleValue.equals(Math.floor(doubleValue));
    }
}
