package dev.langchain4j.spi.classloading;

import java.lang.annotation.Annotation;
import java.util.Optional;

/**
 * A factory interface for providing access to class metadata. Intended to be implemented by downstream frameworks.
 * <p>
 *     {@code dev.langchain4j.classloading.ReflectionBasedClassMetadataProviderFactory}
 *     provides an implementation that uses reflection, which is probably fine in most cases, but this provides hooks for
 *     other frameworks (like Quarkus) which don't use reflection to provide class metadata.
 * </p>
 * <p>
 *     It is asked about AI service classes and their methods, for the AI service guardrail annotations, and also about
 *     tool classes, for the tool guardrail annotations ({@code ToolInputGuardrails} and {@code ToolOutputGuardrails}):
 *     {@link #getAnnotation(Class, Class)} is called with the class of the object a {@code @Tool} method was found on
 *     (or, for a tool that was not found on an object, the class that declares its method), and with each of its
 *     superclasses and interfaces. A factory must return {@link Optional#empty()} for an
 *     annotation or a class it has no metadata for, and must not throw.
 * </p>
 *
 * @param <MethodKey> The type of the method key, representing a unique identifier for methods. Can be whatever it needs to be.
 */
public interface ClassMetadataProviderFactory<MethodKey> {
    /**
     * Retrieves an annotation of the specified type from the given method.
     *
     * @param <T> The type of the annotation to locate, which must extend {@link Annotation}.
     * @param method The method from which the annotation is to be retrieved.
     * @param annotationClass The class object corresponding to the annotation type to find.
     * @return An {@code Optional} containing the located annotation, or an empty {@code Optional} if the annotation
     *         is not present on the specified method.
     */
    <T extends Annotation> Optional<T> getAnnotation(MethodKey method, Class<T> annotationClass);

    /**
     * Retrieves an annotation of the specified type from the given class.
     *
     * @param <T> The type of the annotation to locate, which must extend {@link Annotation}.
     * @param clazz The class from which the annotation is to be retrieved.
     * @param annotationClass The class object corresponding to the annotation type to find.
     * @return An {@code Optional} containing the located annotation, or an empty {@code Optional} if the annotation
     *         is not present on the specified class.
     */
    <T extends Annotation> Optional<T> getAnnotation(Class<?> clazz, Class<T> annotationClass);

    /**
     * Retrieves an iterable containing method keys for all non-static methods defined in the specified class.
     *
     * @param clazz The class from which to retrieve methods.
     * @return An iterable of method keys corresponding to the methods of the specified class.
     */
    Iterable<MethodKey> getNonStaticMethodsOnClass(Class<?> clazz);
}
