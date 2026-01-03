package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Exceptions.unwrapRuntimeException;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.tool.ToolExecutionRequestUtil.argumentsAsMap;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolMemoryId;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.invocation.LangChain4jManaged;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class DefaultToolExecutor implements ToolExecutor {

    private final Object object;
    private final Method originalMethod;
    private final Method methodToInvoke;
    private final boolean wrapToolArgumentsExceptions;
    private final boolean propagateToolExecutionExceptions;

    public DefaultToolExecutor(Builder builder) {
        this.object = ensureNotNull(builder.object, "object");
        this.originalMethod = ensureNotNull(builder.originalMethod, "originalMethod");
        this.methodToInvoke = ensureNotNull(builder.methodToInvoke, "methodToInvoke");
        this.wrapToolArgumentsExceptions = getOrDefault(builder.wrapToolArgumentsExceptions, false);
        this.propagateToolExecutionExceptions = getOrDefault(builder.propagateToolExecutionExceptions, false);
    }

    public DefaultToolExecutor(Object object, Method method) {
        this.object = ensureNotNull(object, "object");
        this.originalMethod = ensureNotNull(method, "method");
        this.methodToInvoke = this.originalMethod;
        this.wrapToolArgumentsExceptions = false;
        this.propagateToolExecutionExceptions = false;
    }

    public DefaultToolExecutor(Object object, ToolExecutionRequest toolExecutionRequest) {
        this.object = ensureNotNull(object, "object");
        ensureNotNull(toolExecutionRequest, "toolExecutionRequest");
        this.originalMethod = findMethod(object, toolExecutionRequest);
        this.methodToInvoke = this.originalMethod;
        this.wrapToolArgumentsExceptions = false;
        this.propagateToolExecutionExceptions = false;
    }

    private Method findMethod(Object object, ToolExecutionRequest toolExecutionRequest) {
        String requestedMethodName = toolExecutionRequest.name();

        for (Method method : object.getClass().getDeclaredMethods()) {
            if (method.getName().equals(requestedMethodName)) {
                return method;
            }
        }

        throw new IllegalArgumentException(String.format(
                "Method '%s' is not found in object '%s'",
                requestedMethodName, object.getClass().getName()));
    }

    /**
     * When methods annotated with @Tool are wrapped into proxies (AOP),
     * the parameters of the proxied method do not retain their original names.
     * Therefore, access to the original method is required to retrieve those names.
     *
     * @param object         the object on which the method should be invoked
     * @param originalMethod the original method, used to retrieve parameter names and prepare arguments
     * @param methodToInvoke the method that should actually be invoked
     */
    public DefaultToolExecutor(Object object, Method originalMethod, Method methodToInvoke) {
        this.object = ensureNotNull(object, "object");
        this.originalMethod = ensureNotNull(originalMethod, "originalMethod");
        this.methodToInvoke = ensureNotNull(methodToInvoke, "methodToInvoke");
        this.wrapToolArgumentsExceptions = false;
        this.propagateToolExecutionExceptions = false;
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
        Object[] arguments = prepareArguments(request, context);

        try {
            return execute(arguments);
        } catch (IllegalAccessException e) {
            try {
                methodToInvoke.setAccessible(true);
                return execute(arguments);
            } catch (IllegalAccessException e2) {
                throw new RuntimeException(e2);
            } catch (InvocationTargetException e2) {
                if (propagateToolExecutionExceptions) {
                    throw new ToolExecutionException(e2.getCause());
                } else {
                    return ToolExecutionResult.builder()
                            .isError(true)
                            .resultText(e2.getCause().getMessage())
                            .build();
                }
            }
        } catch (InvocationTargetException e) {
            if (propagateToolExecutionExceptions) {
                throw new ToolExecutionException(e.getCause());
            } else {
                return ToolExecutionResult.builder()
                        .isError(true)
                        .resultText(e.getCause().getMessage())
                        .build();
            }
        }
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        InvocationContext invocationContext =
                InvocationContext.builder().chatMemoryId(memoryId).build();

        ToolExecutionResult result = executeWithContext(request, invocationContext);

        return result.resultText();
    }

    private Object[] prepareArguments(ToolExecutionRequest toolExecutionRequest, InvocationContext context) {
        try {
            Map<String, Object> argumentsMap = argumentsAsMap(toolExecutionRequest.arguments());
            return prepareArguments(originalMethod, argumentsMap, context);
        } catch (Exception e) {
            if (wrapToolArgumentsExceptions) {
                throw new ToolArgumentsException(unwrapRuntimeException(e));
            } else {
                throw e;
            }
        }
    }

    private ToolExecutionResult execute(Object[] arguments) throws IllegalAccessException, InvocationTargetException {
        Object result = methodToInvoke.invoke(object, arguments);
        return ToolExecutionResult.builder()
                .result(result)
                .resultTextSupplier(() -> toText(result))
                .build();
    }

    private String toText(Object result) {
        Class<?> returnType = methodToInvoke.getReturnType();
        if (returnType == void.class) {
            return "Success";
        } else if (returnType == String.class) {
            return (String) result;
        } else {
            return Json.toJson(result);
        }
    }

    static Object[] prepareArguments(Method method, Map<String, Object> argumentsMap, InvocationContext context) {
        Parameter[] parameters = method.getParameters();
        Object[] arguments = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {

            Parameter parameter = parameters[i];

            if (parameter.isAnnotationPresent(ToolMemoryId.class)) {
                arguments[i] = context.chatMemoryId();
                continue;
            }

            if (InvocationParameters.class.isAssignableFrom(parameter.getType())) {
                arguments[i] = context.invocationParameters();
                continue;
            }

            if (parameter.getType() == InvocationContext.class) {
                arguments[i] = context;
                continue;
            }

            if (LangChain4jManaged.class.isAssignableFrom(parameter.getType())) {
                arguments[i] = context.managedParameters().get(parameter.getType());
                continue;
            }

            String parameterName = parameter.getName();
            Object argument = argumentsMap.get(parameterName);
            if (argument != null) {
                Class<?> parameterClass = parameter.getType();
                Type parameterType = parameter.getParameterizedType();

                arguments[i] = coerceArgument(argument, parameterName, parameterClass, parameterType);
            }
        }

        return arguments;
    }

    static Object coerceArgument(Object argument, String parameterName, Class<?> parameterClass, Type parameterType) {
        if (parameterClass == String.class) {
            return argument.toString();
        }

        if (parameterClass.isEnum()) {
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Class<Enum> enumClass = (Class<Enum>) parameterClass;
                try {
                    return Enum.valueOf(
                            enumClass, Objects.requireNonNull(argument).toString());
                } catch (IllegalArgumentException e) {
                    // try to convert to uppercase as a last resort
                    return Enum.valueOf(
                            enumClass,
                            Objects.requireNonNull(argument).toString().toUpperCase());
                }
            } catch (Exception | Error e) {
                throw new IllegalArgumentException(
                        String.format(
                                "Argument \"%s\" is not a valid enum value for %s: <%s>",
                                parameterName, parameterClass.getName(), argument),
                        e);
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
            checkBounds(doubleValue, parameterName, parameterClass, -Float.MAX_VALUE, Float.MAX_VALUE);
            return (float) doubleValue;
        }

        if (parameterClass == BigDecimal.class) {
            return BigDecimal.valueOf(getDoubleValue(argument, parameterName, parameterClass));
        }

        if (parameterClass == Integer.class || parameterClass == int.class) {
            return (int)
                    getBoundedLongValue(argument, parameterName, parameterClass, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }

        if (parameterClass == Long.class || parameterClass == long.class) {
            return getBoundedLongValue(argument, parameterName, parameterClass, Long.MIN_VALUE, Long.MAX_VALUE);
        }

        if (parameterClass == Short.class || parameterClass == short.class) {
            return (short)
                    getBoundedLongValue(argument, parameterName, parameterClass, Short.MIN_VALUE, Short.MAX_VALUE);
        }

        if (parameterClass == Byte.class || parameterClass == byte.class) {
            return (byte) getBoundedLongValue(argument, parameterName, parameterClass, Byte.MIN_VALUE, Byte.MAX_VALUE);
        }

        if (parameterClass == BigInteger.class) {
            return BigDecimal.valueOf(getNonFractionalDoubleValue(argument, parameterName, parameterClass))
                    .toBigInteger();
        }

        if (Collection.class.isAssignableFrom(parameterClass) || Map.class.isAssignableFrom(parameterClass)) {
            // Conversion to JSON and back is required when parameterType is a POJO
            return Json.fromJson(Json.toJson(argument), parameterType);
        }

        if (parameterClass == UUID.class) {
            return UUID.fromString(argument.toString());
        }

        if (argument instanceof String) {
            return Json.fromJson(argument.toString(), parameterClass);
        } else {
            // Conversion to JSON and back is required when parameterClass is a POJO
            return Json.fromJson(Json.toJson(argument), parameterClass);
        }
    }

    private static double getDoubleValue(Object argument, String parameterName, Class<?> parameterType) {
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

    private static double getNonFractionalDoubleValue(Object argument, String parameterName, Class<?> parameterType) {
        double doubleValue = getDoubleValue(argument, parameterName, parameterType);
        if (!hasNoFractionalPart(doubleValue)) {
            throw new IllegalArgumentException(String.format(
                    "Argument \"%s\" has non-integer value for %s: <%s>",
                    parameterName, parameterType.getName(), argument));
        }
        return doubleValue;
    }

    private static void checkBounds(
            double doubleValue, String parameterName, Class<?> parameterType, double minValue, double maxValue) {
        if (doubleValue < minValue || doubleValue > maxValue) {
            throw new IllegalArgumentException(String.format(
                    "Argument \"%s\" is out of range for %s: <%s>",
                    parameterName, parameterType.getName(), doubleValue));
        }
    }

    public static long getBoundedLongValue(
            Object argument, String parameterName, Class<?> parameterType, long minValue, long maxValue) {
        double doubleValue = getNonFractionalDoubleValue(argument, parameterName, parameterType);
        checkBounds(doubleValue, parameterName, parameterType, minValue, maxValue);
        return (long) doubleValue;
    }

    static boolean hasNoFractionalPart(Double doubleValue) {
        return doubleValue.equals(Math.floor(doubleValue));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Object object;
        private Method originalMethod;
        private Method methodToInvoke;
        private Boolean wrapToolArgumentsExceptions;
        private Boolean propagateToolExecutionExceptions;

        public Builder object(Object object) {
            this.object = object;
            return this;
        }

        public Builder originalMethod(Method originalMethod) {
            this.originalMethod = originalMethod;
            return this;
        }

        public Builder methodToInvoke(Method methodToInvoke) {
            this.methodToInvoke = methodToInvoke;
            return this;
        }

        /**
         * If set to {@code true}, exceptions that occur during tool argument parsing or preparation
         * will be wrapped in a {@link ToolArgumentsException}.
         * <p>
         * The default value is {@code false}.
         */
        public Builder wrapToolArgumentsExceptions(Boolean wrapToolArgumentsExceptions) {
            this.wrapToolArgumentsExceptions = wrapToolArgumentsExceptions;
            return this;
        }

        /**
         * If set to {@code true}, exceptions that occur during tool execution will be thrown
         * instead of being returned as an exception message string.
         * These exceptions will be wrapped in a {@link ToolExecutionException}.
         * <p>
         * The default value is {@code false}.
         */
        public Builder propagateToolExecutionExceptions(Boolean propagateToolExecutionExceptions) {
            this.propagateToolExecutionExceptions = propagateToolExecutionExceptions;
            return this;
        }

        public DefaultToolExecutor build() {
            return new DefaultToolExecutor(this);
        }
    }
}
