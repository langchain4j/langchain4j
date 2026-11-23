package dev.langchain4j.spi.classloading;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * A factory interface for providing access to class metadata. Intended to be implemented by downstream frameworks.
 * <p>
 *     {@code dev.langchain4j.classloading.ReflectionBasedClassMetadataProviderFactory}
 *     provides an implementation that uses reflection, which is probably fine in most cases, but this provides hooks for
 *     other frameworks (like Quarkus) which don't use reflection to provide class metadata.
 * </p>
 * <p>
 *     The method keys this factory returns from {@link #getNonStaticMethodsOnClass(Class)} identify methods wherever
 *     the caller needs to refer to a method of a class it scanned. Code that is handed a {@link Method} instead (for
 *     example the AI service method being invoked) uses {@link #resolveMethodKey(Method)} to find the key that
 *     corresponds to it, which matters for a factory whose keys are not {@link Method}s.
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

    /**
     * Resolves the method key of this factory that corresponds to a {@link Method}.
     * <p>
     *     The keys returned by {@link #getNonStaticMethodsOnClass(Class)} are what this factory uses to identify
     *     methods. A caller that only has the {@link Method} (for example, the AI service method being invoked) calls
     *     this method to find the corresponding key. A factory whose keys are not {@link Method}s overrides it to
     *     translate one into the other.
     * </p>
     * <p>
     *     Callers may cache the result, so the same method must always resolve to the same key.
     * </p>
     * <p>
     *     The default implementation returns an empty {@code Optional}, so a caller falls back to the {@link Method}
     *     itself, which is what a factory whose keys are {@link Method}s needs. Such a factory may also override it to
     *     return the method, like the reflection based one does.
     * </p>
     *
     * @param method The method to resolve the key of. Never {@code null}.
     * @return An {@code Optional} containing the key this factory uses for the method, or an empty {@code Optional}
     *         if this factory has no key for it.
     * @since 1.25.0
     */
    default Optional<MethodKey> resolveMethodKey(Method method) {
        return Optional.empty();
    }
}
