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

public class DefaultToolExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultToolExecutor.class);

    private final Object object;
    private final Method method;

    public DefaultToolExecutor(Object object, Method method) {
        this.object = object;
        this.method = method;
    }

    public String execute(ToolExecutionRequest toolExecutionRequest) {
        log.debug("About to execute {}", toolExecutionRequest);

        // TODO ensure this method never throws exceptions

        Object[] arguments = prepareArguments(method, ToolExecutionRequestUtil.argumentsAsMap(toolExecutionRequest.arguments()));
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

    private String execute(Object[] arguments) throws IllegalAccessException, InvocationTargetException {
        Object result = method.invoke(object, arguments);
        if (method.getReturnType() == void.class) {
            return "Success";
        }
        return Json.toJson(result);
    }

    private static Object[] prepareArguments(Method method, Map<String, Object> argumentsMap) {
        Parameter[] parameters = method.getParameters();
        Object[] arguments = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            String parameterName = parameters[i].getName();
            if (argumentsMap.containsKey(parameterName)) {
                Object argument = argumentsMap.get(parameterName);
                Class<?> parameterType = parameters[i].getType();

                // Gson always parses numbers into the Double type. If the parameter type is not Double, a conversion attempt is made.
                if (argument instanceof Double && !(parameterType == Double.class || parameterType == double.class)) {
                    Double doubleValue = (Double) argument;

                    if (parameterType == Float.class || parameterType == float.class) {
                        if (doubleValue < -Float.MAX_VALUE || doubleValue > Float.MAX_VALUE) {
                            throw new IllegalArgumentException("Double value " + doubleValue + " is out of range for the float type");
                        }
                        argument = doubleValue.floatValue();
                    } else if (parameterType == BigDecimal.class) {
                        argument = BigDecimal.valueOf(doubleValue);
                    }

                    // Allow conversion to integer types only if double value has no fractional part
                    if (hasNoFractionalPart(doubleValue)) {
                        if (parameterType == Integer.class || parameterType == int.class) {
                            if (doubleValue < Integer.MIN_VALUE || doubleValue > Integer.MAX_VALUE) {
                                throw new IllegalArgumentException("Double value " + doubleValue + " is out of range for the integer type");
                            }
                            argument = doubleValue.intValue();
                        } else if (parameterType == Long.class || parameterType == long.class) {
                            if (doubleValue < Long.MIN_VALUE || doubleValue > Long.MAX_VALUE) {
                                throw new IllegalArgumentException("Double value " + doubleValue + " is out of range for the long type");
                            }
                            argument = doubleValue.longValue();
                        } else if (parameterType == Short.class || parameterType == short.class) {
                            if (doubleValue < Short.MIN_VALUE || doubleValue > Short.MAX_VALUE) {
                                throw new IllegalArgumentException("Double value " + doubleValue + " is out of range for the short type");
                            }
                            argument = doubleValue.shortValue();
                        } else if (parameterType == Byte.class || parameterType == byte.class) {
                            if (doubleValue < Byte.MIN_VALUE || doubleValue > Byte.MAX_VALUE) {
                                throw new IllegalArgumentException("Double value " + doubleValue + " is out of range for the byte type");
                            }
                            argument = doubleValue.byteValue();
                        } else if (parameterType == BigInteger.class) {
                            argument = BigDecimal.valueOf(doubleValue).toBigInteger();
                        }
                    }
                }

                arguments[i] = argument;
            }
        }

        return arguments;
    }

    private static boolean hasNoFractionalPart(Double doubleValue) {
        return doubleValue.equals(Math.floor(doubleValue));
    }
}
