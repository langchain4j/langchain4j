package dev.langchain4j.agent.tool;

import dev.langchain4j.internal.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.agent.tool.ToolExecutionRequestUtil.argumentsAsMap;

public class DefaultToolExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultToolExecutor.class);

    private final Object object;
    private final Method method;

    public DefaultToolExecutor(Object object, Method method) {
        this.object = Objects.requireNonNull(object, "object");
        this.method = Objects.requireNonNull(method, "method");
    }

    public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
        log.debug("About to execute {} for memoryId {}", toolExecutionRequest, memoryId);

        // TODO ensure this method never throws exceptions

        Object[] arguments = prepareArguments(
                method, argumentsAsMap(toolExecutionRequest.arguments()), memoryId);
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

            if (parameters[i].isAnnotationPresent(ToolMemoryId.class)) {
                arguments[i] = memoryId;
                continue;
            }

            String parameterName = parameters[i].getName();
            if (argumentsMap.containsKey(parameterName)) {
                Object argument = argumentsMap.get(parameterName);
                Class<?> parameterType = parameters[i].getType();

                arguments[i] = coerceArgument(argument, parameterName, parameterType);
            }
        }

        return arguments;
    }

    static Object coerceArgument(
            Object argument,
            String parameterName,
            Class<?> parameterType
    ) {
        if (parameterType == String.class) {
            return argument.toString();
        }

        if (parameterType.isEnum()) {
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Class<Enum> enumClass = (Class<Enum>) parameterType;
                return Enum.valueOf(enumClass, Objects.requireNonNull(argument.toString()));
            } catch (Exception|Error e) {
                throw new IllegalArgumentException(String.format(
                        "Argument \"%s\" is not a valid enum value for %s: <%s>",
                        parameterName, parameterType.getName(), argument), e);
            }
        }

        if (parameterType == Boolean.class || parameterType == boolean.class) {
            if (argument instanceof Boolean) {
                return argument;
            }
            throw new IllegalArgumentException(String.format(
                    "Argument \"%s\" is not convertable to %s, got %s: <%s>",
                    parameterName, parameterType.getName(), argument.getClass().getName(), argument));
        }

        if (parameterType == Double.class || parameterType == double.class) {
            return getDoubleValue(argument, parameterName, parameterType);
        }

        if (parameterType == Float.class || parameterType == float.class) {
            double doubleValue = getDoubleValue(argument, parameterName, parameterType);
            checkBounds(doubleValue, parameterName, parameterType, -Float.MIN_VALUE, Float.MAX_VALUE);
            return (float) doubleValue;
        }

        if (parameterType == BigDecimal.class) {
            return BigDecimal.valueOf(getDoubleValue(argument, parameterName, parameterType));
        }

        if (parameterType == Integer.class || parameterType == int.class) {
            return (int) getBoundedLongValue(
                    argument, parameterName, parameterType, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }

        if (parameterType == Long.class || parameterType == long.class) {
            return getBoundedLongValue(
                    argument, parameterName, parameterType, Long.MIN_VALUE, Long.MAX_VALUE);
        }

        if (parameterType == Short.class || parameterType == short.class) {
            return (short) getBoundedLongValue(
                    argument, parameterName, parameterType, Short.MIN_VALUE, Short.MAX_VALUE);
        }

        if (parameterType == Byte.class || parameterType == byte.class) {
            return (byte) getBoundedLongValue(
                    argument, parameterName, parameterType, Byte.MIN_VALUE, Byte.MAX_VALUE);
        }

        if (parameterType == BigInteger.class) {
            return BigDecimal.valueOf(
                    getNonFractionalDoubleValue(argument, parameterName, parameterType)).toBigInteger();
        }

        String result  = Json.toJson(argument);
        return Json.fromJson(result, parameterType);
    }

    private static double getDoubleValue(
            Object argument,
            String parameterName,
            Class<?> parameterType
    ) {
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
                    parameterName, parameterType.getName(),doubleValue));
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
