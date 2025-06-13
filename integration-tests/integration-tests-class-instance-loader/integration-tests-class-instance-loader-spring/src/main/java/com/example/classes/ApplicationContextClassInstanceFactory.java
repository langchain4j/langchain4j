package com.example.classes;

import com.example.ApplicationContextProvider;
import dev.langchain4j.spi.classloading.ClassInstanceFactory;

public class ApplicationContextClassInstanceFactory implements ClassInstanceFactory {
    @Override
    public <T> T getInstanceOfClass(Class<T> clazz) {
        return ApplicationContextProvider.getApplicationContext().getBean(clazz);
    }
}
