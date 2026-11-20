package com.example;

import dev.langchain4j.spi.classloading.ClassMetadataProviderFactory;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * A {@link ClassMetadataProviderFactory} whose method keys are strings, as a framework that does not work with
 * {@link Method}s would have. {@link #resolveMethodKey(Method)} tells the guardrail service how the {@link Method}
 * an AI service passes when it is invoked maps to those keys.
 */
public class StringKeyedClassMetadataProviderFactory implements ClassMetadataProviderFactory<String> {
    private static final Map<String, Method> METHODS_BY_KEY = new ConcurrentHashMap<>();

    @Override
    public <T extends Annotation> Optional<T> getAnnotation(String methodKey, Class<T> annotationClass) {
        return Optional.ofNullable(METHODS_BY_KEY.get(methodKey)).map(method -> method.getAnnotation(annotationClass));
    }

    @Override
    public <T extends Annotation> Optional<T> getAnnotation(Class<?> clazz, Class<T> annotationClass) {
        return Optional.ofNullable(clazz.getAnnotation(annotationClass));
    }

    @Override
    public Iterable<String> getNonStaticMethodsOnClass(Class<?> clazz) {
        return Stream.of(clazz.getMethods())
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .map(method -> {
                    var key = keyOf(method);
                    METHODS_BY_KEY.put(key, method);
                    return key;
                })
                .toList();
    }

    @Override
    public Optional<String> resolveMethodKey(Method method) {
        return Optional.of(keyOf(method));
    }

    private static String keyOf(Method method) {
        return method.getDeclaringClass().getName() + "#" + method.getName()
                + Arrays.toString(method.getParameterTypes());
    }
}
