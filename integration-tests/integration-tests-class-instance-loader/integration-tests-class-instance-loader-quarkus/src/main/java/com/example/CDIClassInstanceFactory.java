package com.example;

import dev.langchain4j.spi.classloading.ClassInstanceFactory;
import io.quarkus.logging.Log;
import jakarta.enterprise.inject.spi.CDI;

public class CDIClassInstanceFactory implements ClassInstanceFactory {
    @Override
    public <T> T getInstanceOfClass(Class<T> clazz) {
        Log.infof("Getting instance of class %s from CDI.", clazz.getName());
        return CDI.current().select(clazz).get();
    }
}
