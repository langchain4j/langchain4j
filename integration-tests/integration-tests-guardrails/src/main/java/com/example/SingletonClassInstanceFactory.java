package com.example;

import dev.langchain4j.spi.classloading.ClassInstanceFactory;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A factory for providing class instances that are singletons
 */
public class SingletonClassInstanceFactory implements ClassInstanceFactory {
    private static final ConcurrentMap<Class<?>, Object> INSTANCES = new ConcurrentHashMap<>(5);

    public static <T> T getInstance(Class<T> clazz) {
        if (clazz == InputGuardrailValidation.class) {
            return (T) InputGuardrailValidation.getInstance();
        }

        if (clazz == OutputGuardrailValidation.class) {
            return (T) OutputGuardrailValidation.getInstance();
        }

        return getClassInstance(clazz);
    }

    public static void clearInstances() {
        INSTANCES.clear();
    }

    @Override
    public <T> T getInstanceOfClass(Class<T> clazz) {
        return getInstance(clazz);
    }

    private static <T> T getClassInstance(Class<T> clazz) {
        return (T) INSTANCES.computeIfAbsent(clazz, SingletonClassInstanceFactory::createNewClassInstance);
    }

    private static <T> T createNewClassInstance(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
