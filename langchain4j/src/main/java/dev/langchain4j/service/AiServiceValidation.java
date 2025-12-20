package dev.langchain4j.service;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;
import static java.lang.reflect.Modifier.isStatic;

class AiServiceValidation {

    private static final Set<Method> VALID_METHODS = new HashSet<>();

    private AiServiceValidation() { }

    static void validate(AiServiceContext context) {
        Class<?> serviceClass = context.aiServiceClass;
        validateContextMemory(serviceClass, context.hasChatMemory());
        validateClass(serviceClass);
        Stream.of(serviceClass.getMethods()).forEach(m ->
                validateMethod(serviceClass, m, context.hasChatMemory(), context.hasModerationModel()));
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
                    "The type implemented by the AI Service must be an interface, found '%s'",
                    serviceClass.getName());
        }
    }

    private static void validateMethod(Class<?> serviceClass, Method method, boolean hasChatMemory, boolean hasModerationModel) {
        if (isStatic(method.getModifiers())) {
            // ignore static methods
            return;
        }

        if (!hasModerationModel && method.isAnnotationPresent(Moderate.class)) {
            throw illegalConfiguration(
                    "The @Moderate annotation is present, but the moderationModel is not set up. "
                            + "Please ensure a valid moderationModel is configured before using the @Moderate annotation.");
        }

        Class<?> returnType = method.getReturnType();
        if (returnType == Result.class || returnType == List.class || returnType == Set.class) {
            TypeUtils.validateReturnTypesAreProperlyParametrized(method.getName(), method.getGenericReturnType());
        }

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
            if (checkParamTypeUniqueness(InvocationParameters.class, p, serviceClass, method, invocationParametersExist)) {
                invocationParametersExist = true;
                continue;
            }
            if (checkParamTypeUniqueness(ChatRequestParameters.class, p, serviceClass, method, chatRequestParametersExist)) {
                chatRequestParametersExist = true;
                continue;
            }

            if (LangChain4jManaged.class.isAssignableFrom(p.getType())) {
                continue;
            }

            if (!ParameterNameResolver.hasName(p) && p.getAnnotation(UserMessage.class) == null &&
                    p.getAnnotation(MemoryId.class) == null && p.getAnnotation(UserName.class) == null) {
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

    private static boolean checkParamTypeUniqueness(Class<?> paramType, Parameter p, Class<?> serviceClass, Method method, boolean paramExists) {
        if (paramType.isAssignableFrom(p.getType())) {
            if (paramExists) {
                throw illegalConfiguration(
                        "The method '%s' of the class %s has more than one parameter of type %s",
                        method.getName(),
                        serviceClass.getName(),
                        paramType.getName());
            }
            return true;
        }
        return false;
    }
}
