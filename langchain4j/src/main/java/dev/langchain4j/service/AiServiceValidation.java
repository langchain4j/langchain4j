package dev.langchain4j.service;

import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;
import static java.lang.reflect.Modifier.isStatic;
import static java.util.stream.Collectors.joining;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

class AiServiceValidation {

    private static final Set<Method> VALID_METHODS = new HashSet<>();

    /**
     * LangChain4j types that AI Services return as-is, without asking the LLM to produce them.
     */
    private static final Set<Class<?>> SUPPORTED_RETURN_TYPES =
            Set.of(AiMessage.class, ChatResponse.class, Response.class, TokenStream.class);

    /**
     * LangChain4j types that are supported as the content of a {@link Result}, e.g. {@code Result<AiMessage>}.
     */
    private static final Set<Class<?>> SUPPORTED_RESULT_CONTENT_TYPES = Set.of(AiMessage.class, Response.class);

    /**
     * Packages containing LangChain4j types. Types from these packages are not valid content types.
     */
    private static final Set<String> LANGCHAIN4J_PACKAGES =
            Set.of("dev.langchain4j.agent.", "dev.langchain4j.data.", "dev.langchain4j.model.", "dev.langchain4j.rag.");

    /**
     * LangChain4j types that are not valid content types,
     * but live in a package that is also used by application code (e.g. in tests).
     */
    private static final Set<Class<?>> LANGCHAIN4J_TYPES = Set.of(Result.class, TokenStream.class);

    private AiServiceValidation() {}

    static void validate(AiServiceContext context) {
        Class<?> serviceClass = context.aiServiceClass;
        validateContextMemory(serviceClass, context.hasChatMemory());
        validateClass(serviceClass);
        Stream.of(serviceClass.getMethods())
                .forEach(m -> validateMethod(serviceClass, m, context.hasChatMemory(), context.hasModerationModel()));
    }

    private static void validateContextMemory(Class<?> serviceClass, boolean hasChatMemory) {
        if (!hasChatMemory && ChatMemoryAccess.class.isAssignableFrom(serviceClass)) {
            throw illegalConfiguration(
                    "In order to have a service implementing ChatMemoryAccess, please configure the ChatMemoryProvider on the '%s'.",
                    serviceClass.getName());
        }
    }

    private static void validateClass(Class<?> serviceClass) {
        if (!serviceClass.isInterface()) {
            throw illegalConfiguration(
                    "The type implemented by the AI Service must be an interface, found '%s'", serviceClass.getName());
        }
    }

    private static void validateMethod(
            Class<?> serviceClass, Method method, boolean hasChatMemory, boolean hasModerationModel) {
        if (isStatic(method.getModifiers()) || method.isDefault()) {
            return; // static and default methods are not implemented by the AI Service, they are invoked as-is
        }

        if (!hasModerationModel && method.isAnnotationPresent(Moderate.class)) {
            throw illegalConfiguration("The @Moderate annotation is present, but the moderationModel is not set up. "
                    + "Please ensure a valid moderationModel is configured before using the @Moderate annotation.");
        }

        Class<?> returnType = method.getReturnType();
        if (returnType == Result.class || returnType == List.class || returnType == Set.class) {
            TypeUtils.validateReturnTypesAreProperlyParametrized(method.getName(), method.getGenericReturnType());
        }
        validateReturnType(method.getName(), method.getGenericReturnType());

        if (!hasChatMemory) {
            for (Parameter parameter : method.getParameters()) {
                if (parameter.isAnnotationPresent(MemoryId.class)) {
                    throw illegalConfiguration(
                            "In order to use @MemoryId, please configure the ChatMemoryProvider on the '%s'.",
                            serviceClass.getName());
                }
            }
        }
    }

    private static void validateReturnType(String methodName, Type returnType) {
        if (!isResolvable(returnType)) {
            return;
        }

        Class<?> rawReturnType = TypeUtils.getRawClass(returnType);
        if (SUPPORTED_RETURN_TYPES.contains(rawReturnType) || isImage(returnType)) {
            return;
        }

        if (rawReturnType == Result.class) {
            Type contentType = TypeUtils.resolveFirstGenericParameterType(returnType);
            if (isResolvable(contentType) && SUPPORTED_RESULT_CONTENT_TYPES.contains(TypeUtils.getRawClass(contentType))) {
                return;
            }
            validateContentType(methodName, returnType, contentType);
        } else {
            validateContentType(methodName, returnType, returnType);
        }
    }

    private static void validateContentType(String methodName, Type returnType, Type contentType) {
        if (!isResolvable(contentType)) {
            return;
        }

        Class<?> rawContentType = TypeUtils.getRawClass(contentType);
        if (Collection.class.isAssignableFrom(rawContentType)) {
            validateContentType(methodName, returnType, TypeUtils.resolveFirstGenericParameterType(contentType));
            return;
        }

        if (isLangChain4jType(rawContentType)) {
            throw illegalConfiguration(
                    "The return type '%s' of the method '%s' is invalid: '%s' is a LangChain4j type, "
                            + "so it cannot be parsed from the LLM output. Please return a type the LLM can produce "
                            + "(e.g. String, an enum or a POJO). Metadata about the invocation "
                            + "(token usage, sources, tool executions, the final ChatResponse) is available via 'Result<T>'.",
                    typeName(returnType), methodName, rawContentType.getSimpleName());
        }
    }

    /**
     * Mirrors the image detection in {@code DefaultAiServices}: images are returned as-is, not parsed from text.
     */
    private static boolean isImage(Type type) {
        Class<?> rawClass = TypeUtils.getRawClass(type);
        if (TypeUtils.isImageType(rawClass)) {
            return true;
        }
        if (Collection.class.isAssignableFrom(rawClass)) {
            Class<?> genericParameter = TypeUtils.resolveFirstGenericParameterClass(type);
            return genericParameter != null && TypeUtils.isImageType(genericParameter);
        }
        return false;
    }

    private static boolean isLangChain4jType(Class<?> type) {
        return LANGCHAIN4J_TYPES.contains(type)
                || LANGCHAIN4J_PACKAGES.stream().anyMatch(p -> type.getName().startsWith(p));
    }

    private static boolean isResolvable(Type type) {
        return type instanceof Class<?> || type instanceof ParameterizedType;
    }

    private static String typeName(Type type) {
        if (type instanceof ParameterizedType parameterizedType) {
            return TypeUtils.getRawClass(parameterizedType).getSimpleName()
                    + Stream.of(parameterizedType.getActualTypeArguments())
                            .map(AiServiceValidation::typeName)
                            .collect(joining(", ", "<", ">"));
        }
        return type instanceof Class<?> clazz ? clazz.getSimpleName() : type.getTypeName();
    }

    static void validateParameters(Class<?> serviceClass, Method method) {
        if (!VALID_METHODS.add(method)) {
            return;
        }

        Parameter[] parameters = method.getParameters();
        if (parameters == null || parameters.length < 2) {
            return;
        }

        boolean invocationParametersExist = false;
        boolean chatRequestParametersExist = false;

        for (Parameter p : parameters) {
            if (checkParamTypeUniqueness(
                    InvocationParameters.class, p, serviceClass, method, invocationParametersExist)) {
                invocationParametersExist = true;
                continue;
            }
            if (checkParamTypeUniqueness(
                    ChatRequestParameters.class, p, serviceClass, method, chatRequestParametersExist)) {
                chatRequestParametersExist = true;
                continue;
            }

            if (LangChain4jManaged.class.isAssignableFrom(p.getType())) {
                continue;
            }

            if (!ParameterNameResolver.hasName(p)
                    && p.getAnnotation(UserMessage.class) == null
                    && p.getAnnotation(MemoryId.class) == null
                    && p.getAnnotation(UserName.class) == null) {
                throw illegalConfiguration(
                        "The parameter '%s' in the method '%s' of the class %s must be annotated with either "
                                + "%s, %s, %s, or %s, or it should be of type %s or %s",
                        p.getName(),
                        method.getName(),
                        serviceClass.getName(),
                        dev.langchain4j.service.UserMessage.class.getName(),
                        V.class.getName(),
                        MemoryId.class.getName(),
                        UserName.class.getName(),
                        InvocationParameters.class.getName(),
                        ChatRequestParameters.class.getName());
            }
        }
    }

    private static boolean checkParamTypeUniqueness(
            Class<?> paramType, Parameter p, Class<?> serviceClass, Method method, boolean paramExists) {
        if (paramType.isAssignableFrom(p.getType())) {
            if (paramExists) {
                throw illegalConfiguration(
                        "The method '%s' of the class %s has more than one parameter of type %s",
                        method.getName(), serviceClass.getName(), paramType.getName());
            }
            return true;
        }
        return false;
    }
}
