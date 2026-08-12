package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Exceptions.unwrapRuntimeException;
import static dev.langchain4j.internal.Utils.allConcreteMethods;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.tool.ToolExecutionRequestUtil.argumentsAsMap;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolMemoryId;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.invocation.LangChain4jManaged;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    public Method originalMethod() {
        return originalMethod;
    }

    private Method findMethod(Object object, ToolExecutionRequest toolExecutionRequest) {
        String requestedMethodName = toolExecutionRequest.name();

        for (Method method : allConcreteMethods(object.getClass())) {
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
                            .resultText(errorMessage(e2.getCause()))
                            .build();
                }
            }
        } catch (InvocationTargetException e) {
            if (propagateToolExecutionExceptions) {
                throw new ToolExecutionException(e.getCause());
            } else {
                return ToolExecutionResult.builder()
                        .isError(true)
                        .resultText(errorMessage(e.getCause()))
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
            return prepareArguments(originalMethod, toolExecutionRequest.name(), argumentsMap, context);
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

        List<Content> resultContents = toContents(result);
        if (resultContents != null) {
            return ToolExecutionResult.builder()
                    .result(result)
                    .resultContents(resultContents)
                    .build();
        }

        return ToolExecutionResult.builder()
                .result(result)
                .resultTextSupplier(() -> toText(result))
                .build();
    }

    private List<Content> toContents(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof Image image) {
            return List.of(ImageContent.from(image));
        } else if (result instanceof Content content) {
            return List.of(content);
        } else if (result instanceof Collection<?> collection
                && !collection.isEmpty()
                && collection.iterator().next() instanceof Content) {
            return collection.stream().map(Content.class::cast).toList();
        } else if (result instanceof Content[] array) {
            return List.of(array);
        }
        return null;
    }

    private String toText(Object result) {
        Class<?> returnType = methodToInvoke.getReturnType();
        if (returnType == void.class) {
            return "Success";
        } else if (returnType == String.class) {
            if (result == null) {
                return "null";
            }
            return (String) result;
        } else {
            return Json.toJson(result);
        }
    }

    static Object[] prepareArguments(
            Method method, String toolName, Map<String, Object> argumentsMap, InvocationContext context) {
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

            String parameterName = getName(parameter);
            Object argument = argumentsMap.get(parameterName);
            Class<?> parameterClass = parameter.getType();
            Type parameterType = parameter.getParameterizedType();

            if (parameterClass == Optional.class) {
                arguments[i] = createOptional(argument, parameterName, parameterType);
            } else if (argument != null) {
                arguments[i] = coerceArgument(argument, parameterName, parameterClass, parameterType);
            } else {
                P pAnnotation = parameter.getAnnotation(P.class);
                if (pAnnotation != null && !P.NO_DEFAULT.equals(pAnnotation.defaultValue())) {
                    arguments[i] =
                            parseDefaultValue(pAnnotation.defaultValue(), parameterName, parameterClass, parameterType);
                } else if (parameterClass.isPrimitive()) {
                    throw new IllegalArgumentException(String.format(
                            "Required parameter \"%s\" of tool \"%s\" is missing", parameterName, toolName));
                }
            }
        }

        return arguments;
    }

    private static String errorMessage(Throwable cause) {
        String message = cause.getMessage();
        return message != null ? message : cause.getClass().getName();
    }

    private static String getName(Parameter parameter) {
        P pAnnotation = parameter.getAnnotation(P.class);
        if (pAnnotation != null && isNotNullOrBlank(pAnnotation.name())) {
            return pAnnotation.name();
        }
        return parameter.getName();
    }

    private static Type extractActualType(Type parameterType) {
        return ((ParameterizedType) parameterType).getActualTypeArguments()[0];
    }

    private static Class<?> extractActualClass(Type actualType) {
        return actualType instanceof Class
                ? (Class<?>) actualType
                : (Class<?>) ((ParameterizedType) actualType).getRawType();
    }

    private static Optional<?> createOptional(Object argument, String parameterName, Type parameterType) {
        if (argument == null) {
            return Optional.empty();
        }

        Type actualType = extractActualType(parameterType);
        Class<?> actualClass = extractActualClass(actualType);
        Object coercedValue = coerceArgument(argument, parameterName, actualClass, actualType);
        return Optional.of(coercedValue);
    }

    static Object parseDefaultValue(
            String defaultValue, String parameterName, Class<?> parameterClass, Type parameterType) {
        if (parameterClass == String.class || parameterClass.isEnum() || parameterClass == UUID.class) {
            return coerceArgument(defaultValue, parameterName, parameterClass, parameterType);
        }
        Object jsonParsed;
        try {
            jsonParsed = Json.fromJson(defaultValue, Object.class);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    String.format(
                            "Cannot parse @P(defaultValue = \"%s\") for parameter \"%s\" of type %s: %s",
                            defaultValue, parameterName, parameterClass.getName(), e.getMessage()),
                    e);
        }
        if (jsonParsed == null) {
            throw new IllegalArgumentException(String.format(
                    "@P(defaultValue = \"%s\") parses to null for parameter \"%s\" of type %s",
                    defaultValue, parameterName, parameterClass.getName()));
        }
        return coerceArgument(jsonParsed, parameterName, parameterClass, parameterType);
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
                            Objects.requireNonNull(argument).toString().toUpperCase(Locale.ROOT));
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
            return getBigDecimalValue(argument, parameterName, parameterClass);
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
            return getBigIntegerValue(argument, parameterName, parameterClass);
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
        BigInteger bigIntegerValue = getBigIntegerValue(argument, parameterName, parameterType);
        if (bigIntegerValue.compareTo(BigInteger.valueOf(minValue)) < 0
                || bigIntegerValue.compareTo(BigInteger.valueOf(maxValue)) > 0) {
            throw new IllegalArgumentException(String.format(
                    "Argument \"%s\" is out of range for %s: <%s>", parameterName, parameterType.getName(), argument));
        }
        return bigIntegerValue.longValue();
    }

    /**
     * Converts the argument to a {@link BigInteger} preserving its exact value.
     * Going through {@code double} would silently lose precision for magnitudes above 2^53
     * (e.g. a long 9007199254740993 would become 9007199254740992).
     */
    private static BigInteger getBigIntegerValue(Object argument, String parameterName, Class<?> parameterType) {
        BigDecimal bigDecimalValue = getBigDecimalValue(argument, parameterName, parameterType);
        try {
            return bigDecimalValue.toBigIntegerExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(String.format(
                    "Argument \"%s\" has non-integer value for %s: <%s>",
                    parameterName, parameterType.getName(), argument));
        }
    }

    /**
     * Converts the argument to a {@link BigDecimal} preserving its exact value.
     * Unlike converting through {@code double}, this does not lose precision for large integers
     * or introduce floating-point representation error.
     */
    private static BigDecimal getBigDecimalValue(Object argument, String parameterName, Class<?> parameterType) {
        if (argument instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (argument instanceof BigInteger bigInteger) {
            return new BigDecimal(bigInteger);
        }
        // Long/Integer/Short/Byte have exact string representations; Double/Float are rendered via
        // Number.toString() (matching the behavior of IsEqualTo's numeric comparison).
        if (argument instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (argument instanceof String) {
            try {
                // Trim to tolerate surrounding whitespace, matching the leniency of Double.parseDouble
                // that the previous double-based conversion relied on.
                return new BigDecimal(argument.toString().trim());
            } catch (NumberFormatException e) {
                // fall through to the error below
            }
        }
        throw new IllegalArgumentException(String.format(
                "Argument \"%s\" is not convertable to %s, got %s: <%s>",
                parameterName,
                parameterType.getName(),
                argument == null ? "null" : argument.getClass().getName(),
                argument));
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
