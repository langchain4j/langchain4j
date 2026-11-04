package dev.langchain4j.test.service;

import dev.langchain4j.invocation.InvocationContext;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

/**
 * Finds the return type of the AI Service method of an invocation: {@link InvocationContext#methodReturnType()}, or,
 * for frameworks that do not set it, the declaration found by the name and the number of arguments of the method.
 */
final class AiServiceMethods {

    private AiServiceMethods() {}

    /**
     * Returns whether the AI Service method of the given invocation is declared to return the given type (or a
     * subtype). {@code false} if the return type is not known.
     */
    static boolean returns(InvocationContext context, Class<?> type) {
        if (context != null && context.methodReturnType() != null) {
            Class<?> rawType = TextTypes.rawType(context.methodReturnType());
            return rawType != null && type.isAssignableFrom(rawType);
        }
        return methods(context).stream().anyMatch(method -> type.isAssignableFrom(method.getReturnType()));
    }

    /**
     * Returns the declared return type of the AI Service method of the given invocation, or {@code null} if it is not
     * known (the AI Service interface cannot be loaded, or several of its methods match).
     */
    static Type returnType(InvocationContext context) {
        if (context != null && context.methodReturnType() != null) {
            return context.methodReturnType();
        }
        List<Method> methods = methods(context);
        return methods.size() == 1 ? methods.get(0).getGenericReturnType() : null;
    }

    private static List<Method> methods(InvocationContext context) {
        if (context == null) {
            return List.of();
        }
        Class<?> aiServiceClass = loadClass(context.interfaceName());
        if (aiServiceClass == null) {
            return List.of();
        }
        int argumentCount = context.methodArguments() == null ? 0 : context.methodArguments().size();
        return Arrays.stream(aiServiceClass.getMethods())
                .filter(method -> method.getName().equals(context.methodName())
                        && method.getParameterCount() == argumentCount)
                .toList();
    }

    private static Class<?> loadClass(String className) {
        if (className == null) {
            return null;
        }
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        for (ClassLoader classLoader : new ClassLoader[] {contextClassLoader, AiServiceMethods.class.getClassLoader()}) {
            try {
                return Class.forName(className, false, classLoader);
            } catch (ClassNotFoundException | LinkageError e) {
                // try the next class loader
            }
        }
        return null;
    }
}
