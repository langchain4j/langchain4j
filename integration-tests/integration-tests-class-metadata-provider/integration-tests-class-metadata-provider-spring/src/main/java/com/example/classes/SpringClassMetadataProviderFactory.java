package com.example.classes;

import dev.langchain4j.spi.classloading.ClassMetadataProviderFactory;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

public class SpringClassMetadataProviderFactory implements ClassMetadataProviderFactory<Method> {
    @Override
    public <T extends Annotation> Optional<T> getAnnotation(Method method, Class<T> annotationClass) {
        return Optional.ofNullable(AnnotationUtils.findAnnotation(method, annotationClass));
    }

    @Override
    public <T extends Annotation> Optional<T> getAnnotation(Class<?> clazz, Class<T> annotationClass) {
        return Optional.ofNullable(AnnotationUtils.findAnnotation(clazz, annotationClass));
    }

    @Override
    public Iterable<Method> getNonStaticMethodsOnClass(Class<?> clazz) {
        return Stream.of(ReflectionUtils.getDeclaredMethods(clazz))
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .toList();
    }
}
