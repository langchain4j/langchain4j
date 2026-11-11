package dev.langchain4j.test.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;

/**
 * Tells what the AI Service method of an invocation returns, from its declared return type:
 * {@link InvocationContext#methodReturnType()}, or, for frameworks that do not set it, the declaration found by the
 * name and the number of arguments of the method.
 */
final class AiServiceMethods {

    private AiServiceMethods() {}

    /**
     * Returns whether the AI Service method of the given invocation is declared to return the given type (or a
     * subtype). {@code false} if the return type is not known.
     */
    static boolean returns(InvocationContext context, Class<?> type) {
        if (context.methodReturnType() != null) {
            Class<?> rawType = rawType(context.methodReturnType());
            return rawType != null && type.isAssignableFrom(rawType);
        }
        return methods(context).stream().anyMatch(method -> type.isAssignableFrom(method.getReturnType()));
    }

    /**
     * Returns the declared return type of the AI Service method of the given invocation, or {@code null} if it is not
     * known (the AI Service interface cannot be loaded, or several of its methods match).
     */
    static Type returnType(InvocationContext context) {
        if (context.methodReturnType() != null) {
            return context.methodReturnType();
        }
        List<Method> methods = methods(context);
        return methods.size() == 1 ? methods.get(0).getGenericReturnType() : null;
    }

    /**
     * Returns whether the AI Service method of the given invocation returns only when it is done: whether it is
     * declared to return neither a stream nor an asynchronous value. {@code false} if it is not known.
     */
    static boolean blocks(InvocationContext context) {
        Type returnType = returnType(context);
        Class<?> rawType = returnType == null ? null : rawType(returnType);
        return rawType != null
                && !TokenStream.class.isAssignableFrom(rawType)
                && !isPublisher(rawType)
                && !isSingleValue(rawType);
    }

    static boolean isNothing(Type type) {
        return type == void.class || type == Void.class;
    }

    static boolean isText(Type type) {
        Class<?> rawType = rawType(type);
        if (rawType == null) {
            return false; // not known, for example a type variable
        }
        if (rawType == String.class
                || AiMessage.class.isAssignableFrom(rawType)
                || ChatResponse.class.isAssignableFrom(rawType)
                || TokenStream.class.isAssignableFrom(rawType)
                || isPublisher(rawType)) {
            return true;
        }
        if (Result.class.isAssignableFrom(rawType) || isSingleValue(rawType)) {
            return !(type instanceof ParameterizedType parameterized)
                    || isText(parameterized.getActualTypeArguments()[0]);
        }
        return false;
    }

    private static boolean isPublisher(Class<?> rawType) {
        return Flow.Publisher.class.isAssignableFrom(rawType) || isA(rawType, "org.reactivestreams.Publisher");
    }

    private static boolean isSingleValue(Class<?> rawType) {
        return CompletionStage.class.isAssignableFrom(rawType)
                || Future.class.isAssignableFrom(rawType)
                || isA(rawType, "io.smallrye.mutiny.Uni")
                || isA(rawType, "reactor.core.publisher.Mono");
    }

    private static Class<?> rawType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        return null;
    }

    /**
     * Returns whether the given type is the class with the given name or a subtype of it, for a class of a library
     * that may not be on the classpath.
     */
    static boolean isA(Class<?> type, String className) {
        Class<?> named = loadClass(type.getClassLoader(), className);
        return named != null && named.isAssignableFrom(type);
    }

    private static List<Method> methods(InvocationContext context) {
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
        Class<?> loaded = loadClass(Thread.currentThread().getContextClassLoader(), className);
        return loaded != null ? loaded : loadClass(AiServiceMethods.class.getClassLoader(), className);
    }

    /**
     * Returns the class with the given name, or {@code null} if the given class loader does not find it.
     */
    static Class<?> loadClass(ClassLoader classLoader, String className) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}
