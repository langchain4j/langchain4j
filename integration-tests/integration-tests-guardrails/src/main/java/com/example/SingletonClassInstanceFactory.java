package com.example;

import dev.langchain4j.spi.classloading.ClassInstanceFactory;
import java.lang.reflect.InvocationTargetException;

public class SingletonClassInstanceFactory implements ClassInstanceFactory {
    @Override
    public <T> T getInstanceOfClass(Class<T> clazz) {
        return (clazz == GuardrailValidation.class)
                ? (T) GuardrailValidation.getInstance()
                : createNewClassInstance(clazz);
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
