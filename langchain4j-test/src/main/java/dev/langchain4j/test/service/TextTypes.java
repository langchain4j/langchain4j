package dev.langchain4j.test.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;

/**
 * Tells whether an AI Service method produces text or blocks, from its declared return type.
 */
final class TextTypes {

    private static final Set<String> WRAPPERS = Set.of("io.smallrye.mutiny.Uni", "reactor.core.publisher.Mono");
    private static final Set<String> PUBLISHERS = Set.of("org.reactivestreams.Publisher");

    private TextTypes() {}

    /**
     * Returns whether the AI Service method of the given invocation returns only when it is done: whether it is
     * declared to return neither a stream nor an asynchronous value. {@code false} if it is not known.
     */
    static boolean blocks(InvocationContext context) {
        Type returnType = AiServiceMethods.returnType(context);
        Class<?> rawType = returnType == null ? null : rawType(returnType);
        return rawType != null
                && !TokenStream.class.isAssignableFrom(rawType)
                && !isPublisher(rawType)
                && !CompletionStage.class.isAssignableFrom(rawType)
                && !Future.class.isAssignableFrom(rawType)
                && !isAssignableTo(rawType, WRAPPERS);
    }

    private static boolean isPublisher(Class<?> rawType) {
        return Flow.Publisher.class.isAssignableFrom(rawType) || isAssignableTo(rawType, PUBLISHERS);
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
                || Flow.Publisher.class.isAssignableFrom(rawType)
                || isAssignableTo(rawType, PUBLISHERS)) {
            return true;
        }
        if (Result.class.isAssignableFrom(rawType)
                || CompletionStage.class.isAssignableFrom(rawType)
                || Future.class.isAssignableFrom(rawType)
                || isAssignableTo(rawType, WRAPPERS)) {
            return !(type instanceof ParameterizedType parameterized)
                    || isText(parameterized.getActualTypeArguments()[0]);
        }
        return false;
    }

    static Class<?> rawType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        return null;
    }

    static boolean isAssignableTo(Class<?> type, Set<String> classNames) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (classNames.contains(current.getName())) {
                return true;
            }
            for (Class<?> implemented : current.getInterfaces()) {
                if (classNames.contains(implemented.getName()) || isAssignableTo(implemented, classNames)) {
                    return true;
                }
            }
        }
        return false;
    }
}
