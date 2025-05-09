package com.example.classloading;

import dev.langchain4j.spi.classloading.ClassInstanceFactory;

public class GenericClassInstanceFactory implements ClassInstanceFactory {
    @Override
    public <T> T getInstanceOfClass(Class<T> clazz) {
        return Classes.getInstance().getInstance(clazz);
    }
}
